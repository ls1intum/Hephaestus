package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.core.security.MissingCredentialKeyException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one way a read path turns a connection's stored credential into plaintext. A credential the
 * configured keys cannot read is recorded on the connection and surfaces as
 * {@link CredentialUnreadableException} rather than as an unexplained server error; a credential
 * that reads again clears the record. A key version this server holds no key for is an instance
 * fault and passes through untouched, as it does for the rotation job.
 *
 * <p>Every decryption failure other than a missing key is treated the same way: a truncated or
 * unsupported ciphertext, an authentication failure under the configured keys and an undeserializable
 * bundle are each a property of the stored row against this server, and only rewriting the row or
 * restoring the key it was written with changes any of them.
 *
 * <p>The record is written by a conditional update bound to the exact ciphertext that failed, so a
 * credential replaced between the failed read and the write is never marked, and it is written by a
 * worker of the reader's own, never on the caller's thread, so a burst of failing reads never holds
 * two pooled connections each and a stalled database never delays the answer. It skips a row another transaction holds locked rather than waiting, and
 * a failure to write it is logged; neither ever replaces the answer.
 */
@Service
public class CredentialReader {

    private static final Logger log = LoggerFactory.getLogger(CredentialReader.class);

    /** The thread every record is written from. */
    static final String RECORDER_THREAD = "credential-readability";

    private final ConnectionRepository connectionRepository;
    private final CredentialBundleConverter converter;
    private final TransactionTemplate markTransaction;
    private final Clock clock;
    /**
     * One worker and a small queue: a record that finds the queue full, or arrives after shutdown, is
     * dropped, because a later read of the same credential makes the same record again and a stalled
     * database must not turn every failing read into retained work.
     */
    private final ExecutorService recorder = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64),
            runnable -> {
                Thread thread = new Thread(runnable, RECORDER_THREAD);
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    public CredentialReader(
            ConnectionRepository connectionRepository,
            CredentialBundleConverter converter,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.converter = converter;
        this.markTransaction = new TransactionTemplate(transactionManager);
        this.markTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public Optional<CredentialBundle> credentialsOf(Connection connection) {
        byte[] ciphertext = connection.getCredentialsEncrypted();
        try {
            Optional<CredentialBundle> credentials = connection.credentials(converter);
            if (ciphertext != null && connection.getCredentialsRotationFailedAt() != null) {
                Long id = connection.getId();
                Long workspaceId = connection.getWorkspace().getId();
                record(() -> connectionRepository.markCredentialsReadable(id, workspaceId, ciphertext));
            }
            return credentials;
        } catch (MissingCredentialKeyException unconfiguredKey) {
            throw unconfiguredKey;
        } catch (EncryptionException undecryptable) {
            Long id = connection.getId();
            if (id != null && ciphertext != null) {
                Long workspaceId = connection.getWorkspace().getId();
                Instant now = clock.instant();
                record(() -> connectionRepository.markCredentialsUnreadable(id, workspaceId, ciphertext, now));
            }
            throw new CredentialUnreadableException(id == null ? -1 : id, connection.getKind(), undecryptable);
        }
    }

    /**
     * Hands the record's update to the recorder, which runs it in a transaction of its own on one
     * connection at a time. Never on the caller's thread: inside a transaction the caller's connection
     * stays checked out until the transaction is fully cleaned up, and a stalled database must not
     * delay the answer the caller already has. The record is best effort; a later read repeats it.
     */
    private void record(Runnable update) {
        recorder.execute(() -> {
            try {
                markTransaction.executeWithoutResult(status -> update.run());
            } catch (RuntimeException e) {
                log.warn("Could not record the readability of a stored credential", e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        recorder.shutdown();
        try {
            // Briefly, so a record already in flight lands before the data source goes; a stalled one
            // is abandoned like any other.
            if (!recorder.awaitTermination(2, TimeUnit.SECONDS)) {
                recorder.shutdownNow();
            }
        } catch (InterruptedException e) {
            recorder.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

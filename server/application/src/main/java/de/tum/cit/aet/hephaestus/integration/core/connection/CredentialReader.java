package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.core.security.MissingCredentialKeyException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one way a read path turns a connection's stored credential into plaintext. A credential the
 * configured keys cannot read is recorded on the connection, in its own transaction so the mark
 * survives the failure of whatever asked, and surfaces as {@link CredentialUnreadableException}
 * rather than as an unexplained server error. A key version this server holds no key for is an
 * instance fault and passes through untouched, as it does for the rotation job.
 */
@Service
public class CredentialReader {

    private final ConnectionRepository connectionRepository;
    private final CredentialBundleConverter converter;
    private final TransactionTemplate markTransaction;
    private final Clock clock;

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
        try {
            return connection.credentials(converter);
        } catch (MissingCredentialKeyException unconfiguredKey) {
            throw unconfiguredKey;
        } catch (EncryptionException undecryptable) {
            Long id = connection.getId();
            if (id != null) {
                markTransaction.executeWithoutResult(status -> connectionRepository
                        .findById(id)
                        .ifPresent(row -> row.markCredentialRotationFailed(clock.instant())));
            }
            throw new CredentialUnreadableException(id == null ? -1 : id, connection.getKind());
        }
    }
}

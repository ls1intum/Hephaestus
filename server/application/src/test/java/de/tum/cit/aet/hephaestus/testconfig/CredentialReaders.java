package de.tum.cit.aet.hephaestus.testconfig;

import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialReader;
import java.time.Clock;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Readers for unit tests of the callers that decrypt through one — the credential providers, the
 * connection services — rather than of the reader itself.
 */
public final class CredentialReaders {

    private CredentialReaders() {}

    /**
     * A reader whose records go nowhere: the repository is a mock, so no statement needs stubbing, and
     * the executor runs on the calling thread, so nothing is left in flight when the test ends.
     */
    public static CredentialReader forTests(CredentialBundleConverter converter) {
        return new CredentialReader(
                mock(ConnectionRepository.class),
                converter,
                mock(PlatformTransactionManager.class),
                new SyncTaskExecutor(),
                Clock.systemUTC());
    }
}

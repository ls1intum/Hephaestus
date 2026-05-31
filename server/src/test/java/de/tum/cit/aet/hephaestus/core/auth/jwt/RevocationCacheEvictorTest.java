package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Guards the revocation-eviction contract that makes logout / sign-out-everywhere take effect
 * immediately on the acting pod. The subtlety under test is the <em>deferral</em>: eviction MUST
 * wait for the surrounding transaction to commit (so the revoked row is visible before any decode
 * re-reads it), and MUST happen synchronously when there is no transaction.
 */
class RevocationCacheEvictorTest extends BaseUnitTest {

    @Test
    void evictsImmediatelyWhenNoTransactionIsActive() {
        RevocationAwareJwtDecoder decoder = mock(RevocationAwareJwtDecoder.class);
        UUID jti = UUID.randomUUID();

        new RevocationCacheEvictor(decoder).evictAfterCommit(jti);

        verify(decoder).invalidate(jti);
    }

    @Test
    void defersEvictionUntilAfterCommitWhenInTransaction() {
        RevocationAwareJwtDecoder decoder = mock(RevocationAwareJwtDecoder.class);
        UUID jti = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try {
            new RevocationCacheEvictor(decoder).evictAfterCommit(jti);

            // Not yet — evicting inside the open tx could let a concurrent decode re-cache ACTIVE
            // off the not-yet-committed row.
            verifyNoInteractions(decoder);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(decoder).invalidate(jti);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

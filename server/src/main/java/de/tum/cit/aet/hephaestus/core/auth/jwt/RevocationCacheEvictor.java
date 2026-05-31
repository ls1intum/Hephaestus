package de.tum.cit.aet.hephaestus.core.auth.jwt;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts a revoked {@code jti} from {@link RevocationAwareJwtDecoder}'s local cache so that
 * "logout" / "sign out everywhere" / admin-revoke take effect immediately on this pod instead
 * of lingering for the cache TTL.
 *
 * <p>Eviction is deferred to <strong>after the surrounding transaction commits</strong>. If we
 * evicted inside the revoke transaction, a concurrent {@code decode()} of the same token could
 * miss the cache, read the not-yet-committed {@code revoked_at IS NULL} row, and re-cache
 * {@code ACTIVE} — reopening the very window we are closing. After commit the revoked row is
 * visible, so the next decode re-reads {@code REVOKED}. With no active transaction (defensive)
 * we evict synchronously.
 *
 * <p>Cross-pod propagation is not covered here: other replicas still observe the revocation
 * within the {@code auth_jwt_revoked} cache TTL (see {@link RevocationAwareJwtDecoder}). A
 * compromised account is killed cluster-wide immediately by suspending it (the per-request
 * account-status gate), which does not depend on this cache.
 */
@Component
public class RevocationCacheEvictor {

    private final RevocationAwareJwtDecoder decoder;

    RevocationCacheEvictor(RevocationAwareJwtDecoder decoder) {
        this.decoder = decoder;
    }

    public void evictAfterCommit(UUID jti) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        decoder.invalidate(jti);
                    }
                }
            );
        } else {
            decoder.invalidate(jti);
        }
    }
}

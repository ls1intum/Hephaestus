package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use store backing {@link HmacOAuthStateService}. Each issued state writes
 * a row keyed by its nonce; consumption flips {@code consumed_at} via an atomic
 * conditional UPDATE so concurrent callbacks resolve to at-most-one winner.
 *
 * <p>The HMAC guarantees authenticity + TTL; this store guarantees uniqueness.
 * Together they bound an attacker to a single callback even with a captured token
 * inside the TTL window.
 *
 * <p>Kept as a separate component (rather than inlining repository calls into
 * {@link HmacOAuthStateService}) so the store can be unit-tested with mock
 * repositories without dragging in the HMAC machinery and vice-versa.
 */
@Component
@WorkspaceAgnostic("Operates on a global pre-workspace nonce table")
public class OAuthStateNonceStore {

    private static final Logger log = LoggerFactory.getLogger(OAuthStateNonceStore.class);

    private final OAuthStateNonceRepository repository;

    public OAuthStateNonceStore(OAuthStateNonceRepository repository) {
        this.repository = repository;
    }

    /**
     * Record a freshly minted nonce. Persists immediately so a subsequent
     * {@link #tryConsume(String)} sees the row even if the issuing transaction is
     * separate.
     */
    @Transactional
    public void issue(String nonce, long workspaceId, IntegrationKind kind, Instant issuedAt) {
        if (nonce == null || nonce.isEmpty()) {
            throw new IllegalArgumentException("nonce must be non-empty");
        }
        if (repository.existsById(nonce)) {
            // SecureRandom collision on 12 bytes is ~1 in 2^96. This is a defensive
            // guard against the only realistic source — a test re-using a stubbed
            // RNG. Logging at WARN so it surfaces in CI if anyone wires a determ.
            log.warn(
                "OAuth state nonce collision (existing row reused): nonce-prefix={}",
                nonce.substring(0, Math.min(4, nonce.length()))
            );
            return;
        }
        repository.save(new OAuthStateNonce(nonce, workspaceId, kind.name(), issuedAt));
    }

    /**
     * Attempt to consume {@code nonce}. Returns true exactly once per nonce; all
     * subsequent calls return false (whether the previous caller is still in
     * flight or already committed).
     *
     * <p>If the nonce row doesn't exist at all the call also returns false — this
     * covers the case where an attacker forged a state with a guessed nonce but
     * the HMAC happened to (somehow) validate; without a backing row the consume
     * still fails closed. Callers should treat false as "reject and audit".
     */
    @Transactional
    public boolean tryConsume(String nonce) {
        if (nonce == null || nonce.isEmpty()) {
            return false;
        }
        int updated = repository.markConsumed(nonce, Instant.now());
        return updated == 1;
    }
}

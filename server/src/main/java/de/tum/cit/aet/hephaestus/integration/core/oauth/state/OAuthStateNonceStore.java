package de.tum.cit.aet.hephaestus.integration.core.oauth.state;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
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

    /** Issue without PKCE — kept for legacy/non-OAuth flows (App install, PAT). */
    @Transactional
    public void issue(String nonce, long workspaceId, IntegrationKind kind, Instant issuedAt) {
        issue(nonce, workspaceId, kind, issuedAt, null);
    }

    /**
     * Record a freshly minted nonce, optionally with a PKCE {@code code_verifier}.
     * Persists immediately so a subsequent consume sees the row even if the issuing
     * transaction is separate.
     */
    @Transactional
    public void issue(
        String nonce,
        long workspaceId,
        IntegrationKind kind,
        Instant issuedAt,
        @org.jspecify.annotations.Nullable String codeVerifier
    ) {
        if (nonce == null || nonce.isEmpty()) {
            throw new IllegalArgumentException("nonce must be non-empty");
        }
        if (repository.existsById(nonce)) {
            // SecureRandom collision on 12 bytes is ~1 in 2^96. Defensive guard
            // against tests reusing a stubbed RNG.
            log.warn(
                "OAuth state nonce collision (existing row reused): nonce-prefix={}",
                nonce.substring(0, Math.min(4, nonce.length()))
            );
            return;
        }
        repository.save(new OAuthStateNonce(nonce, workspaceId, kind.name(), issuedAt, codeVerifier));
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

    /**
     * Consume the nonce AND read its PKCE verifier in one transaction. The verifier
     * lookup happens immediately before the conditional UPDATE so a replay attacker
     * who races the legitimate callback never observes one without the other.
     *
     * <p>The wrapper record distinguishes "consume failed" (row absent / already
     * consumed) from "consume succeeded but no verifier was persisted" (legacy /
     * non-PKCE flow). Returning a bare {@code Optional<String>} would conflate those
     * two cases and force callers to bolt on a second probe.
     *
     * @return {@link ConsumeResult} with {@code consumed=true} exactly once per nonce;
     *         {@code verifier} present iff a PKCE verifier was persisted at issue time.
     */
    @Transactional
    public ConsumeResult tryConsumeWithVerifier(String nonce) {
        if (nonce == null || nonce.isEmpty()) {
            return ConsumeResult.notConsumed();
        }
        String verifier = repository.findCodeVerifier(nonce);
        int updated = repository.markConsumed(nonce, Instant.now());
        if (updated != 1) {
            return ConsumeResult.notConsumed();
        }
        return ConsumeResult.consumed(verifier);
    }

    /**
     * Outcome of {@link #tryConsumeWithVerifier}. {@code consumed} is the single-use
     * winner-loser bit; {@code verifier} carries the PKCE code_verifier when one was
     * persisted at issue time.
     */
    public record ConsumeResult(boolean consumed, java.util.Optional<String> verifier) {
        public static ConsumeResult notConsumed() {
            return new ConsumeResult(false, java.util.Optional.empty());
        }

        public static ConsumeResult consumed(@org.jspecify.annotations.Nullable String verifier) {
            return new ConsumeResult(true, java.util.Optional.ofNullable(verifier));
        }
    }
}

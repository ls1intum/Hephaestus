package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.time.Instant;

/**
 * Verified worker-JWT claims surfaced to the handshake interceptor. Decouples the {@code
 * com.auth0} types from the rest of the codebase so a future verifier swap (Nimbus, OAuth2
 * resource server) is a one-class change.
 *
 * @param workerId the {@code sub} claim
 * @param jti     the {@code jti} claim, used for denylist lookups
 * @param expiresAt the {@code exp} claim
 */
public record WorkerJwt(String workerId, String jti, Instant expiresAt) {
    public WorkerJwt {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId (sub) must not be blank");
        }
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
    }
}

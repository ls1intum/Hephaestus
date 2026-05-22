package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

/**
 * Strategy interface for verifying worker-presented JWTs. Default impl is
 * {@link JavaJwtWorkerJwtVerifier}; tests can substitute trivially.
 */
public interface WorkerJwtVerifier {
    /**
     * Validate the token, returning its claims if all checks pass.
     *
     * @throws WorkerJwtInvalidException on any verification failure (signature, expiry, claims).
     *     The exception's message is safe to log but not surface to remote callers — the
     *     handshake interceptor maps it to a generic 401.
     */
    WorkerJwt verify(String token) throws WorkerJwtInvalidException;
}

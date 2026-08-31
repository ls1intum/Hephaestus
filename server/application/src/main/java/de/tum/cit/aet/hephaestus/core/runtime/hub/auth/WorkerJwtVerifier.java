package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

public interface WorkerJwtVerifier {
    /**
     * Validate the token, returning its claims if all checks pass.
     *
     * @throws WorkerJwtInvalidException on any verification failure. Its message must not be
     *     returned to the caller.
     */
    WorkerJwt verify(String token);
}

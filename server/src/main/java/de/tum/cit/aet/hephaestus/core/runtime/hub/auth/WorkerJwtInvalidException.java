package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

/**
 * Thrown by {@link WorkerJwtVerifier} on any failure (bad signature, expired, missing claim,
 * revoked, malformed). The detail message is for log-side diagnosis only — the handshake
 * interceptor maps every variant to a single 401 to avoid attack-surface enumeration.
 */
public class WorkerJwtInvalidException extends Exception {

    public WorkerJwtInvalidException(String message) {
        super(message);
    }

    public WorkerJwtInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/**
 * Hub → worker request to drop and re-handshake. Sent when the worker's JWT is within
 * {@code exp - now < 5min} so the next dial picks up a fresh JWT through token exchange.
 */
public record ForceReconnect(String reason) implements WorkerControlFrame {
    public ForceReconnect {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}

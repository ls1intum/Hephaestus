package de.tum.in.www1.hephaestus.gitprovider.sync.exception;

/**
 * Exception thrown when a sync operation is interrupted.
 * <p>
 * This typically occurs when:
 * <ul>
 *   <li>Thread is interrupted while waiting for rate limits</li>
 *   <li>Application is shutting down during sync</li>
 *   <li>Sync operation is cancelled</li>
 * </ul>
 */
public class SyncInterruptedException extends RuntimeException {

    public SyncInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyncInterruptedException(String message) {
        super(message);
    }
}

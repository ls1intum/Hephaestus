package de.tum.in.www1.hephaestus.gitprovider.sync.exception;

/**
 * Exception thrown when all retry attempts for a sync operation have been exhausted.
 * <p>
 * This typically occurs when a transient failure persists across all configured
 * retry attempts during data synchronization.
 */
public class SyncRetriesExhaustedException extends RuntimeException {

    public SyncRetriesExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyncRetriesExhaustedException(String message) {
        super(message);
    }
}

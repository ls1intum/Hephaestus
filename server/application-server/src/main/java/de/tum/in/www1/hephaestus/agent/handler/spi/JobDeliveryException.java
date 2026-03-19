package de.tum.in.www1.hephaestus.agent.handler.spi;

/**
 * Thrown when a {@link JobTypeHandler} fails to deliver results for a completed job.
 *
 * <p>The executor catches this to mark the job as {@code FAILED} with a clear,
 * handler-provided reason. Unchecked so it can propagate through lambda boundaries
 * (e.g. stream operations, functional interfaces).
 */
public class JobDeliveryException extends RuntimeException {

    public JobDeliveryException(String message) {
        super(message);
    }

    public JobDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

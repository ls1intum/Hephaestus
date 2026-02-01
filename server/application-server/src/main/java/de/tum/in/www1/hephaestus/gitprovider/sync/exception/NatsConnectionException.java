package de.tum.in.www1.hephaestus.gitprovider.sync.exception;

import java.io.Serial;

/**
 * Exception thrown when a NATS connection cannot be established or is lost.
 */
public class NatsConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NatsConnectionException(String message) {
        super(message);
    }

    public NatsConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

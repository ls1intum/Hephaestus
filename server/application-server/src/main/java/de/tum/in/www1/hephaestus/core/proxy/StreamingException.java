package de.tum.in.www1.hephaestus.core.proxy;

import java.io.Serial;

/**
 * Exception thrown when SSE streaming to a client fails, typically due to client disconnection.
 */
class StreamingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StreamingException(String message, Throwable cause) {
        super(message, cause);
    }
}

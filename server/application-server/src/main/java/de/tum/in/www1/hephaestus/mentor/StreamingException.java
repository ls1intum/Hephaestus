package de.tum.in.www1.hephaestus.mentor;

import java.io.Serial;

/**
 * Exception thrown when SSE streaming to a client fails, typically due to client disconnection.
 */
public class StreamingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StreamingException(String message) {
        super(message);
    }

    public StreamingException(String message, Throwable cause) {
        super(message, cause);
    }
}

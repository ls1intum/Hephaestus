package de.tum.in.www1.hephaestus.agent.mentor.chat.exception;

import java.io.Serial;

/** Raised when an SSE send fails because the client socket is gone. Control-flow only. */
public final class ClientDisconnectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Used when the disconnect was observed without a wrapping I/O failure (e.g. the lifecycle
     * flag flipped between sandbox attach and the next send attempt).
     */
    public ClientDisconnectedException(String message) {
        super(message);
    }

    public ClientDisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}

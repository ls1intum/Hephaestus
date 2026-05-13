package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Unchecked exception for interactive sandbox failures.
 *
 * <p>Thrown by {@link InteractiveSandboxService#attach} and {@link AttachedSandbox#send} for all
 * infrastructure errors (Docker unreachable, container start failure, broken stdin pipe, write
 * timeout, write queue full, session already closed). Callers must catch this at attach time and
 * map it to a transport-level error response.
 */
public class InteractiveSandboxException extends RuntimeException {

    public InteractiveSandboxException(String message) {
        super(message);
    }

    public InteractiveSandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

/** Unchecked exception for interactive-sandbox infrastructure failures. */
public class InteractiveSandboxException extends RuntimeException {

    public InteractiveSandboxException(String message) {
        super(message);
    }

    public InteractiveSandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Unchecked exception for sandbox infrastructure failures.
 *
 * <p>Wraps docker-java exceptions and other infrastructure errors so that callers of {@link
 * SandboxManager} do not depend on Docker-specific types.
 */
public class SandboxException extends RuntimeException {

  public SandboxException(String message) {
    super(message);
  }

  public SandboxException(String message, Throwable cause) {
    super(message, cause);
  }
}

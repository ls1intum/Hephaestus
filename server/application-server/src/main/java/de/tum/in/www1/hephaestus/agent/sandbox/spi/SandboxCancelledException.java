package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/** Thrown when a sandbox execution is cancelled before completion. */
public class SandboxCancelledException extends SandboxException {

  public SandboxCancelledException(String message) {
    super(message);
  }
}

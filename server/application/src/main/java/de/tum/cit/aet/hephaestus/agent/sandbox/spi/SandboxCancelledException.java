package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

/** Thrown when a sandbox execution is cancelled before completion. */
public class SandboxCancelledException extends SandboxException {

    public SandboxCancelledException(String message) {
        super(message);
    }
}

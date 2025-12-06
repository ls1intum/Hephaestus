package de.tum.in.www1.hephaestus.workspace.exception;

/**
 * Signals that a requested lifecycle transition violates workspace invariants
 * (e.g., attempting to resume or suspend a purged workspace).
 */
public class WorkspaceLifecycleViolationException extends RuntimeException {

    public WorkspaceLifecycleViolationException(String message) {
        super(message);
    }
}

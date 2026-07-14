package de.tum.cit.aet.hephaestus.integration.core.sync;

import java.io.Serial;

/**
 * Thrown for a genuine state conflict that should surface as {@code 409 Conflict} — a manual sync
 * requested on a non-ACTIVE connection, or a cancel requested on an already-terminal job.
 *
 * <p>Deliberately NOT {@code IllegalStateException}: {@code WorkspaceControllerAdvice} installs a
 * global, {@code HIGHEST_PRECEDENCE} {@code @ExceptionHandler(IllegalStateException.class)} that maps
 * to {@code 500} ("unexpected workspace state") — appropriate for ITS domain, wrong for ours, and
 * unavoidable from here since that advice has no {@code basePackages} scoping. A distinct exception
 * type sidesteps it entirely; {@code SyncController} maps this one to 409 explicitly.
 */
public class SyncStateConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SyncStateConflictException(String message) {
        super(message);
    }
}

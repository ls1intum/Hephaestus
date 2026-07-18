package de.tum.cit.aet.hephaestus.integration.core.sync;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown for a genuine state conflict that should surface as {@code 409 Conflict} — a manual sync
 * requested on a non-ACTIVE connection, or a cancel requested on an already-terminal job.
 *
 * <p>Deliberately NOT {@code IllegalStateException}: {@code WorkspaceControllerAdvice} installs a
 * global, {@code HIGHEST_PRECEDENCE} {@code @ExceptionHandler(IllegalStateException.class)} that maps
 * to {@code 500} ("unexpected workspace state") — appropriate for ITS domain, wrong for ours, and
 * unavoidable from here since that advice has no {@code basePackages} scoping. A distinct exception
 * type sidesteps it entirely; {@code SyncController} maps this one to 409 explicitly.
 *
 * <p>Optionally carries machine-readable {@link #properties()} (RFC 9457 extension members) so the
 * 409 body tells the client the conflicting state — the connection's state, or the in-flight job's
 * id/type/status — without a follow-up refetch.
 */
public class SyncStateConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<String, Object> properties;

    public SyncStateConflictException(String message) {
        this(message, Map.of());
    }

    public SyncStateConflictException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = Map.copyOf(properties);
    }

    /** RFC 9457 extension members the handler copies onto the {@code ProblemDetail} (may be empty). */
    public Map<String, Object> properties() {
        return properties;
    }
}

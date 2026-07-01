package de.tum.cit.aet.hephaestus.practices;

import java.io.Serial;

/**
 * Thrown when attempting to create a practice area with a slug that already exists in the workspace.
 * Mapped to an RFC-7807 {@code ProblemDetail} by {@link PracticesControllerAdvice}, mirroring
 * {@link PracticeSlugConflictException} so both conflicts in this module return the same 409 shape.
 */
public class PracticeAreaSlugConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PracticeAreaSlugConflictException(String message) {
        super(message);
    }

    public PracticeAreaSlugConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

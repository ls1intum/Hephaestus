package de.tum.cit.aet.hephaestus.practices;

import java.io.Serial;

public class PracticeGroupSlugConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PracticeGroupSlugConflictException(String message) {
        super(message);
    }

    public PracticeGroupSlugConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.tum.cit.aet.hephaestus.agent.job;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation is attempted on an agent job in an incompatible state.
 * For example, cancelling a job that has already completed.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ReviewJobStateConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewJobStateConflictException(String message) {
        super(message);
    }

    public ReviewJobStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

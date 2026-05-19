package de.tum.in.www1.hephaestus.agent.job;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation is attempted on an agent job in an incompatible state.
 * For example, cancelling a job that has already completed.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AgentJobStateConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentJobStateConflictException(String message) {
        super(message);
    }
}

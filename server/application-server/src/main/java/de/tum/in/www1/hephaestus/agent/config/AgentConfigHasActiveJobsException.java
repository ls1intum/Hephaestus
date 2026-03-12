package de.tum.in.www1.hephaestus.agent.config;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when attempting to delete an agent config that still has active (QUEUED/RUNNING) jobs.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AgentConfigHasActiveJobsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigHasActiveJobsException(String message) {
        super(message);
    }
}

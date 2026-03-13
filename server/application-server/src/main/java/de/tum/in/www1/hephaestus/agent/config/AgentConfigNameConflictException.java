package de.tum.in.www1.hephaestus.agent.config;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when attempting to create an agent config with a name that already exists in the workspace.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AgentConfigNameConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigNameConflictException(String message) {
        super(message);
    }
}

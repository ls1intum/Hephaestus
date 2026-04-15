package de.tum.in.www1.hephaestus.agent.runner;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AgentRunnerNameConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentRunnerNameConflictException(String message) {
        super(message);
    }
}

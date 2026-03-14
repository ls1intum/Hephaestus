package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an agent type is paired with an incompatible LLM provider.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AgentConfigProviderMismatchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigProviderMismatchException(AgentType agentType, LlmProvider requiredProvider, LlmProvider actual) {
        super(agentType + " agent requires " + requiredProvider + " provider, got: " + actual);
    }
}

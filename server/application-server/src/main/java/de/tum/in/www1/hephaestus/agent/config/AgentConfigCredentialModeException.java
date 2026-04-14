package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a credential mode configuration violates business rules.
 *
 * <p>For example, {@link CredentialMode#API_KEY} and {@link CredentialMode#OAUTH} modes
 * require internet access to be enabled because the container must reach the LLM provider
 * directly.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AgentConfigCredentialModeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigCredentialModeException(CredentialMode mode) {
        super(mode + " credential mode requires internet access to be enabled");
    }

    public AgentConfigCredentialModeException(String message) {
        super(message);
    }
}

package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a credential mode configuration violates business rules: {@link CredentialMode#API_KEY}
 * requires both internet access and a stored credential, because the container reaches the LLM
 * provider directly.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AgentConfigCredentialModeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private AgentConfigCredentialModeException(String message) {
        super(message);
    }

    public static AgentConfigCredentialModeException requiresInternet(CredentialMode mode) {
        return new AgentConfigCredentialModeException(mode + " credential mode requires internet access to be enabled");
    }

    public static AgentConfigCredentialModeException missingCredential(CredentialMode mode) {
        return new AgentConfigCredentialModeException(
            mode + " credential mode requires an API key/credential to be set"
        );
    }
}

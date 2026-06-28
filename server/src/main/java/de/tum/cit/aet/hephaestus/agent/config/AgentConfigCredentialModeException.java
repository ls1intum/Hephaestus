package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import java.io.Serial;

/**
 * Thrown when a credential mode configuration violates business rules: {@link CredentialMode#API_KEY}
 * requires both internet access and a stored credential, because the container reaches the LLM
 * provider directly.
 *
 * <p>HTTP mapping (status/title/detail) is owned solely by {@code AgentControllerAdvice}; this
 * exception carries no {@code @ResponseStatus} so there is one authoritative mapper.
 */
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

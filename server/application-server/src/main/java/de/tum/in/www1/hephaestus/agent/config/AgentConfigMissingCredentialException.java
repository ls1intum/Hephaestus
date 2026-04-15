package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AgentConfigMissingCredentialException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigMissingCredentialException(CredentialMode mode) {
        super(mode + " credential mode requires a credential to be configured");
    }
}

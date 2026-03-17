package de.tum.in.www1.hephaestus.agent.proxy;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Authentication token representing a validated agent job token.
 *
 * <p>Set on the {@link org.springframework.security.core.context.SecurityContext} by
 * {@link JobTokenAuthenticationFilter} after successful token validation.
 * The {@link AgentJob} is the principal and can be retrieved by the controller.
 */
class JobTokenAuthentication extends AbstractAuthenticationToken {

    private final AgentJob job;

    JobTokenAuthentication(AgentJob job) {
        super(List.of());
        this.job = job;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "[REDACTED]";
    }

    @Override
    public AgentJob getPrincipal() {
        return job;
    }
}

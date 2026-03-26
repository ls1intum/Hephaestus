package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.practices.spi.AgentConfigChecker;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link AgentConfigChecker} backed by {@link AgentConfigRepository}.
 */
@Component
public class AgentConfigCheckerAdapter implements AgentConfigChecker {

    private final AgentConfigRepository agentConfigRepository;

    public AgentConfigCheckerAdapter(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    @Override
    public boolean hasEnabledConfig(Long workspaceId) {
        return agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(workspaceId);
    }
}

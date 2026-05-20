package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
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

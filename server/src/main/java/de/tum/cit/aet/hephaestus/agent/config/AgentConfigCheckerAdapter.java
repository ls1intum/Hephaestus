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

    @Override
    public boolean hasRunnablePracticeConfig(Long workspaceId, Long boundConfigId) {
        if (boundConfigId != null) {
            // Bound: only that specific config runs, and a bound-but-disabled binding pauses detection.
            return agentConfigRepository
                .findByIdAndWorkspaceId(boundConfigId, workspaceId)
                .filter(AgentConfig::isEnabled)
                .isPresent();
        }
        return agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(workspaceId);
    }
}

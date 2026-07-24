package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link AgentConfigChecker} backed by {@link AgentConfigRepository}.
 */
@Component
public class AgentConfigCheckerAdapter implements AgentConfigChecker {

    private final AgentConfigRepository agentConfigRepository;
    private final LlmModelResolver llmModelResolver;

    public AgentConfigCheckerAdapter(AgentConfigRepository agentConfigRepository, LlmModelResolver llmModelResolver) {
        this.agentConfigRepository = agentConfigRepository;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    // Keep this boundary non-transactional: resolve() reports revocation with an exception. Catching that
    // exception inside a shared transaction would still mark the transaction rollback-only.
    public boolean hasRunnablePracticeConfig(Long workspaceId, Long boundConfigId) {
        if (boundConfigId == null) {
            return false; // unbound = detection off; no implicit fan-out to every enabled config (#1368)
        }
        // Bound: only that specific config runs; never fall back to another configuration.
        return agentConfigRepository
            .findByIdAndWorkspaceId(boundConfigId, workspaceId)
            .stream()
            .filter(AgentConfig::isEnabled)
            .anyMatch(this::isModelAvailable);
    }

    private boolean isModelAvailable(AgentConfig config) {
        try {
            llmModelResolver.resolve(config);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}

package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.stereotype.Service;

@Service
@WorkspaceAgnostic("Slack App Home resolves a workspace from Slack team_id before checking mentor readiness")
class DefaultMentorReadinessQuery implements MentorReadinessQuery {

    private final WorkspaceAgentBindingRepository agentBindingRepository;
    private final LlmModelResolver llmModelResolver;

    DefaultMentorReadinessQuery(
        WorkspaceAgentBindingRepository agentBindingRepository,
        LlmModelResolver llmModelResolver
    ) {
        this.agentBindingRepository = agentBindingRepository;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    public boolean isReady(long workspaceId) {
        return agentBindingRepository
            .findByWorkspaceIdAndPurpose(workspaceId, AgentPurpose.MENTOR)
            .filter(WorkspaceAgentBinding::isEnabled)
            .map(this::hasAvailableCatalogModel)
            .orElse(false);
    }

    private boolean hasAvailableCatalogModel(WorkspaceAgentBinding binding) {
        try {
            return llmModelResolver.resolve(binding) != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}

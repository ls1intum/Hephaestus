package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import org.springframework.stereotype.Service;

@Service
@WorkspaceAgnostic("Slack App Home resolves a workspace from Slack team_id before checking mentor config readiness")
class DefaultMentorReadinessQuery implements MentorReadinessQuery {

    private final WorkspaceSummaryQuery workspaceSummaryQuery;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmModelResolver llmModelResolver;

    DefaultMentorReadinessQuery(
        WorkspaceSummaryQuery workspaceSummaryQuery,
        AgentConfigRepository agentConfigRepository,
        LlmModelResolver llmModelResolver
    ) {
        this.workspaceSummaryQuery = workspaceSummaryQuery;
        this.agentConfigRepository = agentConfigRepository;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    public boolean isReady(long workspaceId) {
        return workspaceSummaryQuery
            .findById(workspaceId)
            .map(workspace -> {
                Long mentorConfigId = workspace.mentorConfigId();
                if (mentorConfigId == null) {
                    return false;
                }
                return agentConfigRepository
                    .findByIdAndWorkspaceId(mentorConfigId, workspaceId)
                    .filter(AgentConfig::isEnabled)
                    .map(this::hasAvailableCatalogModel)
                    .orElse(false);
            })
            .orElse(false);
    }

    private boolean hasAvailableCatalogModel(AgentConfig config) {
        try {
            return llmModelResolver.resolve(config) != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}

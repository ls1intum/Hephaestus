package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import org.springframework.stereotype.Service;

@Service
@WorkspaceAgnostic("Slack App Home resolves a workspace from Slack team_id before checking mentor config readiness")
class DefaultMentorReadinessQuery implements MentorReadinessQuery {

    private final WorkspaceSummaryQuery workspaceSummaryQuery;
    private final AgentConfigRepository agentConfigRepository;

    DefaultMentorReadinessQuery(
        WorkspaceSummaryQuery workspaceSummaryQuery,
        AgentConfigRepository agentConfigRepository
    ) {
        this.workspaceSummaryQuery = workspaceSummaryQuery;
        this.agentConfigRepository = agentConfigRepository;
    }

    @Override
    public boolean isReady(long workspaceId) {
        return workspaceSummaryQuery
            .findById(workspaceId)
            .map(workspace -> {
                Long mentorConfigId = workspace.mentorConfigId();
                if (mentorConfigId != null) {
                    return agentConfigRepository
                        .findByIdAndWorkspaceId(mentorConfigId, workspaceId)
                        .map(config -> config.isEnabled())
                        .orElse(false);
                }
                return agentConfigRepository.findFirstByWorkspaceIdAndEnabledTrueOrderByIdAsc(workspaceId).isPresent();
            })
            .orElse(false);
    }
}

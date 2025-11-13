package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadSubIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubSubIssueMessageHandler extends GitHubMessageHandler<GHEventPayloadSubIssue> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssueMessageHandler.class);

    private final WorkspaceRepository workspaceRepository;
    private final IssueRelationsUpdater relationsUpdater;

    public GitHubSubIssueMessageHandler(
        WorkspaceRepository workspaceRepository,
        IssueRelationsUpdater relationsUpdater
    ) {
        super(GHEventPayloadSubIssue.class);
        this.workspaceRepository = workspaceRepository;
        this.relationsUpdater = relationsUpdater;
    }

    @Override
    protected void handleEvent(GHEventPayloadSubIssue eventPayload) {
        Long installationId = eventPayload.getInstallation() != null ? eventPayload.getInstallation().getId() : null;
        if (installationId == null) {
            logger.warn("Received sub-issue event without installation reference; skipping");
            return;
        }

        Workspace workspace = workspaceRepository
            .findByInstallationId(installationId)
            .orElse(null);

        if (workspace == null) {
            logger.warn("No workspace registered for installation {}. Skipping sub-issue event.", installationId);
            return;
        }

        relationsUpdater.applyWebhook(workspace.getId(), eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.UNKNOWN;
    }

    @Override
    public String getCustomEventName() {
        return "sub_issues";
    }
 }

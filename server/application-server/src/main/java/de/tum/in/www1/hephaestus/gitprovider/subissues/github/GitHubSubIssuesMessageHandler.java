package de.tum.in.www1.hephaestus.gitprovider.subissues.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Message handler for GitHub sub-issues webhook events.
 *
 * Sub-issues is a GitHub beta feature (tasklists) that allows tracking relationships
 * between parent and child issues for task management.
 *
 * Handles four event types:
 * - sub_issue_added: Child issue added to parent
 * - sub_issue_removed: Child issue removed from parent
 * - parent_issue_added: Parent issue linked to child
 * - parent_issue_removed: Parent issue unlinked from child
 */
@Component
public class GitHubSubIssuesMessageHandler extends GitHubMessageHandler<GHEventPayloadSubIssues> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final GitHubIssueSyncService issueSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubSubIssuesMessageHandler(
        GitHubIssueSyncService issueSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayloadSubIssues.class);
        this.issueSyncService = issueSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadSubIssues eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var parentIssue = eventPayload.getParentIssue();
        var subIssue = eventPayload.getSubIssue();

        logger.info(
            "Received sub-issues event for repository: {}, action: {}, parent: {}, sub: {}",
            repository.getFullName(),
            action,
            parentIssue != null ? parentIssue.getNumber() : eventPayload.getParentIssueId(),
            subIssue != null ? subIssue.getNumber() : eventPayload.getSubIssueId()
        );

        repositorySyncService.processRepository(repository);

        if (parentIssue != null) {
            issueSyncService.processIssue(parentIssue);
        }
        if (subIssue != null) {
            issueSyncService.processIssue(subIssue);
        }

        Long parentId = parentIssue != null ? parentIssue.getId() : eventPayload.getParentIssueId();
        Long subIssueId = subIssue != null ? subIssue.getId() : eventPayload.getSubIssueId();

        if (parentId == null || subIssueId == null) {
            logger.warn(
                "Skipping relationship update due to missing identifiers (parent: {}, child: {})",
                parentId,
                subIssueId
            );
            return;
        }

        boolean addRelation =
            switch (action) {
                case "sub_issue_removed", "parent_issue_removed" -> false;
                default -> true;
            };

        if (addRelation) {
            issueSyncService.addSubIssueRelationship(parentId, subIssueId);
        } else {
            issueSyncService.removeSubIssueRelationship(parentId, subIssueId);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        // Since GHEvent.SUB_ISSUES doesn't exist in github-api library,
        // we use ALL as a placeholder. The actual routing is handled by
        // NatsConsumerService which intercepts "sub_issues" before GHEvent.valueOf()
        return GHEvent.ALL;
    }

    @Override
    protected String getCustomEventType() {
        return "sub_issues";
    }
}

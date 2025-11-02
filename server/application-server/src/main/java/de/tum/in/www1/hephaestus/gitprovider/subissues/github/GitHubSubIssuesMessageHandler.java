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
 * Handler for GitHub sub-issues (tasklist) webhook events.
 *
 * Sub-issues are a GitHub beta feature that allows issues to be organized in parent-child hierarchies
 * through tasklists. This handler processes events when issues are added or removed as sub-issues.
 *
 * Supported actions:
 * - sub_issue_added: A child issue is added to a parent issue
 * - sub_issue_removed: A child issue is removed from a parent issue
 * - parent_issue_added: An issue gets a parent issue
 * - parent_issue_removed: An issue's parent is removed
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
        var subIssue = eventPayload.getSubIssue();
        var parentIssue = eventPayload.getParentIssue();

        logger.info(
            "Received sub_issues event for repository: {}, sub-issue: {}, parent-issue: {}, action: {}",
            repository.getFullName(),
            subIssue.getNumber(),
            parentIssue.getNumber(),
            action
        );

        // Ensure the repository is synced
        repositorySyncService.processRepository(repository);

        // Process both the sub-issue and parent issue to ensure they're up-to-date
        // This ensures the relationship metadata (sub_issues_summary, parent_issue_url) is captured
        issueSyncService.processIssue(subIssue);
        issueSyncService.processIssue(parentIssue);

        // If parent issue is in a different repository, sync that too
        var parentRepo = eventPayload.getParentIssueRepo();
        if (parentRepo != null && !parentRepo.getId().equals(repository.getId())) {
            logger.info("Parent issue is in different repository: {}, syncing it as well", parentRepo.getFullName());
            repositorySyncService.processRepository(parentRepo);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.SUB_ISSUES;
    }
}

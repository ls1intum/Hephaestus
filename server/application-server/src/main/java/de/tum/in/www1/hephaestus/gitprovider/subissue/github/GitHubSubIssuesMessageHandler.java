package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.kohsuke.github.GHEventPayloadSubIssues.IssueInfo;
import org.kohsuke.github.GHEventPayloadSubIssues.SubIssuesSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub sub_issues webhook events for parent-child issue
 * relationships.
 * <p>
 * This handler processes four event types:
 * <ul>
 * <li>{@code sub_issue_added} - A sub-issue was added to a parent issue</li>
 * <li>{@code sub_issue_removed} - A sub-issue was removed from a parent
 * issue</li>
 * <li>{@code parent_issue_added} - A parent issue was assigned to a
 * sub-issue</li>
 * <li>{@code parent_issue_removed} - A parent issue was removed from a
 * sub-issue</li>
 * </ul>
 * <p>
 * Uses the hub4j-compatible {@link GHEventPayloadSubIssues} payload for type
 * safety.
 *
 * @see GHEventPayloadSubIssues
 * @see GitHubSubIssueSyncService
 */
@Component
public class GitHubSubIssuesMessageHandler extends GitHubMessageHandler<GHEventPayloadSubIssues> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssuesMessageHandler.class);

    private final GitHubSubIssueSyncService subIssueSyncService;

    public GitHubSubIssuesMessageHandler(GitHubSubIssueSyncService subIssueSyncService) {
        super(GHEventPayloadSubIssues.class);
        this.subIssueSyncService = subIssueSyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadSubIssues payload) {
        var action = payload.getActionType();
        long subIssueId = payload.getSubIssueId();
        long parentIssueId = payload.getParentIssueId();

        // GitHub fires two events for each relationship change:
        // - SUB_ISSUE_* (from parent's perspective) - PRIMARY
        // - PARENT_ISSUE_* (from child's perspective) - DUPLICATE
        // We only process the primary events to avoid double processing.
        if (!action.isPrimaryEvent()) {
            logger.debug(
                "Skipping duplicate {} event (sub_issue={}, parent={}), already processed via SUB_ISSUE_* event",
                action,
                subIssueId,
                parentIssueId
            );
            return;
        }

        logger.info(
            "Received sub_issues event: action={}, subIssue={}, parentIssue={}",
            action,
            subIssueId,
            parentIssueId
        );

        // Extract sub_issues_summary from parent issue (if available)
        SubIssuesSummary parentSummary = extractParentSummary(payload.getParentIssue());

        subIssueSyncService.processSubIssueEvent(subIssueId, parentIssueId, payload.isLinkEvent(), parentSummary);
    }

    /**
     * Extracts the sub_issues_summary from the parent issue if available.
     *
     * @param parentIssue the parent issue from the payload
     * @return the summary, or null if not available
     */
    private SubIssuesSummary extractParentSummary(IssueInfo parentIssue) {
        if (parentIssue == null) {
            return null;
        }
        return parentIssue.getSubIssuesSummary();
    }

    @Override
    protected String getEventKey() {
        return "sub_issues";
    }
}

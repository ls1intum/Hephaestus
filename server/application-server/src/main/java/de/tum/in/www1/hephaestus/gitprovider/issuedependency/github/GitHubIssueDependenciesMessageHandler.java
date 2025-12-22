package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import org.kohsuke.github.GHEventPayloadIssueDependencies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles GitHub issue_dependencies webhook events for blocking relationships.
 * <p>
 * This handler processes two event types:
 * <ul>
 * <li>{@code blocked_by_added} - An issue was marked as blocked by another</li>
 * <li>{@code blocked_by_removed} - A blocking relationship was removed</li>
 * </ul>
 * <p>
 * These events are distinct from sub_issues events - blocking indicates a
 * dependency/prerequisite relationship, while sub_issues indicates hierarchy.
 * <p>
 * <b>NOTE (Dec 2025):</b> The {@code issue_dependencies} webhook event is
 * <b>STILL NOT AVAILABLE</b> for subscription in GitHub App settings.
 * GitHub shipped the "Blocked by" UI feature without webhook support
 * (see <a href="https://github.com/orgs/community/discussions/165749">
 * Community Discussion #165749</a>). API/webhook support was listed under
 * "What we are thinking about next" rather than "What is included."
 * Until webhooks are available, issue dependencies can only be synced via
 * GraphQL bulk queries. This handler is prepared for when webhooks launch.
 *
 * @see GHEventPayloadIssueDependencies
 * @see GitHubIssueDependencySyncService
 */
@Component
public class GitHubIssueDependenciesMessageHandler extends GitHubMessageHandler<GHEventPayloadIssueDependencies> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueDependenciesMessageHandler.class);

    private final GitHubIssueDependencySyncService issueDependencySyncService;

    public GitHubIssueDependenciesMessageHandler(GitHubIssueDependencySyncService issueDependencySyncService) {
        super(GHEventPayloadIssueDependencies.class);
        this.issueDependencySyncService = issueDependencySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadIssueDependencies payload) {
        var action = payload.getActionType();
        long blockedIssueId = payload.getBlockedIssueId();
        long blockingIssueId = payload.getBlockingIssueId();

        logger.info(
            "Received issue_dependencies event: action={}, blockedIssue={}, blockingIssue={}",
            action,
            blockedIssueId,
            blockingIssueId
        );

        issueDependencySyncService.processIssueDependencyEvent(blockedIssueId, blockingIssueId, payload.isBlockEvent());
    }

    @Override
    protected String getEventKey() {
        return "issue_dependencies";
    }
}

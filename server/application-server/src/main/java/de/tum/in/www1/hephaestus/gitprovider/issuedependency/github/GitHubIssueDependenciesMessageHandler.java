package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.dto.GitHubIssueDependenciesEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub {@code issue_dependencies} webhook events.
 * <p>
 * GitHub sends these events when issue blocking/blocked-by relationships change.
 * Actions include {@code added} and {@code removed}. According to GitHub documentation,
 * to receive these events a GitHub App must have at least read-level access for the
 * "Issues" repository permission.
 * <p>
 * <b>IMPORTANT - Webhook Availability Limitation (as of January 2026):</b>
 * <p>
 * While the {@code issue_dependencies} event is documented in GitHub's webhook reference,
 * <b>it cannot actually be subscribed to via GitHub App settings</b>. The event type does
 * not appear in the GitHub App permissions/events configuration UI. This appears to be a
 * gap where GitHub shipped the "Blocked by" UI feature without corresponding webhook support.
 * <p>
 * <b>Current Workaround:</b> Use {@link GitHubIssueDependencySyncService#syncDependenciesForScope}
 * for periodic GraphQL-based synchronization of blocking relationships.
 * <p>
 * <b>Status Tracking:</b>
 * <ul>
 *   <li><a href="https://github.com/orgs/community/discussions/165749">GitHub Community Discussion #165749</a>
 *       - Tracks the feature request for API/webhook support</li>
 *   <li>GitHub's announcement mentions "API and webhook support" as a future goal</li>
 * </ul>
 * <p>
 * This handler is implemented in anticipation of webhook support being enabled. When GitHub
 * adds the event to App settings, this handler will automatically start processing events
 * via the NATS message queue.
 * <p>
 * <b>No test fixtures exist</b> for this event type because webhooks cannot be received
 * to capture real payloads. The DTO structure is based on GitHub's documentation.
 *
 * @see GitHubIssueDependencySyncService for the GraphQL-based sync alternative
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#issue_dependencies">
 *      GitHub Webhook Events - issue_dependencies (documented but not subscribable)</a>
 */
@Component
public class GitHubIssueDependenciesMessageHandler extends GitHubMessageHandler<GitHubIssueDependenciesEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueDependenciesMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueDependencySyncService issueDependencySyncService;

    GitHubIssueDependenciesMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueDependencySyncService issueDependencySyncService,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubIssueDependenciesEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.issueDependencySyncService = issueDependencySyncService;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.ISSUE_DEPENDENCIES;
    }

    @Override
    protected void handleEvent(GitHubIssueDependenciesEventDTO event) {
        var blockedIssueDto = event.blockedIssue();
        var blockingIssueDto = event.blockingIssue();

        if (blockedIssueDto == null || blockingIssueDto == null) {
            log.warn("Received issue_dependencies event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received issue_dependencies event: action={}, blockedIssueNumber={}, blockingIssueNumber={}, repoName={}",
            event.action(),
            blockedIssueDto.number(),
            blockingIssueDto.number(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure both issues exist
        issueProcessor.process(blockedIssueDto, context);
        issueProcessor.process(blockingIssueDto, context);

        // Process dependency relationship
        Long blockedIssueId = blockedIssueDto.getDatabaseId();
        Long blockingIssueId = blockingIssueDto.getDatabaseId();

        switch (event.actionType()) {
            case GitHubEventAction.IssueDependency.ADDED -> issueDependencySyncService.processIssueDependencyEvent(
                blockedIssueId,
                blockingIssueId,
                true
            );
            case GitHubEventAction.IssueDependency.REMOVED -> issueDependencySyncService.processIssueDependencyEvent(
                blockedIssueId,
                blockingIssueId,
                false
            );
            default -> log.debug("Skipped issue_dependencies event: reason=unhandledAction, action={}", event.action());
        }
    }
}

package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub issue_comment webhook events.
 * <p>
 * This handler processes comments on both Issues and Pull Requests. GitHub sends
 * issue_comment events for both entity types, with a pull_request field present
 * when the comment is on a PR.
 * <p>
 * <b>Key Design Decision:</b> For Issues, we always process the parent entity first
 * to ensure it exists. For PRs, we rely on the comment processor to create a stub
 * PR entity if needed, since the PR webhook may not have arrived yet. This prevents
 * the 769+ ParentEntityNotFoundException errors that occurred due to message ordering.
 *
 * @see GitHubIssueCommentProcessor#processWithParentCreation
 */
@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GitHubIssueCommentEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueCommentProcessor commentProcessor;

    GitHubIssueCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueCommentProcessor commentProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubIssueCommentEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.commentProcessor = commentProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.ISSUE_COMMENT;
    }

    @Override
    protected void handleEvent(GitHubIssueCommentEventDTO event) {
        var commentDto = event.comment();
        var issueDto = event.issue();

        if (commentDto == null || issueDto == null) {
            log.warn("Received issue_comment event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received issue_comment event: action={}, issueNumber={}, commentId={}, repoName={}, isPullRequest={}",
            event.action(),
            issueDto.number(),
            commentDto.id(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown",
            issueDto.isPullRequest()
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // For regular Issues: process the Issue first to ensure it exists.
        // This creates/updates the Issue entity before we try to attach a comment.
        if (!issueDto.isPullRequest()) {
            issueProcessor.process(issueDto, context);
        }
        // For PRs: We don't call issueProcessor.process() because that would create
        // an Issue entity with discriminator "ISSUE", but we need "PULL_REQUEST".
        // The PR should be created by pull_request webhooks, but due to message
        // ordering, the comment may arrive first. The comment processor will handle
        // creating a stub PR if needed.

        // Handle comment action
        if (event.actionType() == GitHubEventAction.IssueComment.DELETED) {
            commentProcessor.delete(commentDto.id(), context);
        } else {
            // Use processWithParentCreation to handle the case where the parent
            // entity (Issue or PR) doesn't exist yet. This creates a minimal
            // entity from the webhook data instead of failing.
            commentProcessor.processWithParentCreation(commentDto, issueDto, context);
        }
    }
}

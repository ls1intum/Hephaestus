package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub pull_request_review_comment webhook events.
 * <p>
 * This handler processes review comments on Pull Requests. The webhook includes
 * embedded PR data, which we use to create a stub PR entity if the PR
 * webhook hasn't arrived yet (message ordering issue).
 * <p>
 * <b>Key Design Decision:</b> We use processCreatedWithParentCreation to handle the case
 * where the review comment webhook arrives before the PR webhook. This creates a minimal
 * PR stub from the webhook data, which will be hydrated later by the PR webhook
 * or scheduled sync. This prevents data loss that occurred due to message ordering.
 *
 * @see GitHubPullRequestReviewCommentProcessor#processCreatedWithParentCreation
 */
@Component
public class GitHubPullRequestReviewCommentMessageHandler
    extends GitHubMessageHandler<GitHubPullRequestReviewCommentEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestReviewCommentProcessor commentProcessor;

    GitHubPullRequestReviewCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestReviewCommentProcessor commentProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubPullRequestReviewCommentEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.commentProcessor = commentProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PULL_REQUEST_REVIEW_COMMENT;
    }

    @Override
    protected void handleEvent(GitHubPullRequestReviewCommentEventDTO event) {
        var commentDto = event.comment();
        var prDto = event.pullRequest();

        if (commentDto == null || prDto == null) {
            log.warn("Received pull_request_review_comment event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received pull_request_review_comment event: action={}, prNumber={}, commentId={}, repoName={}",
            event.action(),
            prDto.number(),
            commentDto.id(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        Long prId = prDto.getDatabaseId();

        // Delegate to processor based on action
        // Use processCreatedWithParentCreation to handle the case where the PR webhook
        // hasn't arrived yet. This creates a minimal PR stub from the webhook data
        // instead of losing the comment.
        switch (event.actionType()) {
            case GitHubEventAction.PullRequestReviewComment.DELETED -> commentProcessor.processDeleted(
                commentDto.id(),
                prId,
                context
            );
            case GitHubEventAction.PullRequestReviewComment.CREATED -> commentProcessor.processCreatedWithParentCreation(
                commentDto,
                prDto,
                context
            );
            case GitHubEventAction.PullRequestReviewComment.EDITED -> commentProcessor.processEdited(
                commentDto,
                prId,
                context
            );
            default -> log.debug(
                "Skipped pull_request_review_comment event: reason=unhandledAction, action={}",
                event.action()
            );
        }
    }
}

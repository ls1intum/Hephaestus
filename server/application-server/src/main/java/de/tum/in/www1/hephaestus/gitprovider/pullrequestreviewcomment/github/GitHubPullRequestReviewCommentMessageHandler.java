package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review_comment webhook events.
 */
@Component
public class GitHubPullRequestReviewCommentMessageHandler
    extends GitHubMessageHandler<GitHubPullRequestReviewCommentEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final GitHubPullRequestReviewCommentProcessor commentProcessor;

    GitHubPullRequestReviewCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        GitHubPullRequestReviewCommentProcessor commentProcessor
    ) {
        super(GitHubPullRequestReviewCommentEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.commentProcessor = commentProcessor;
    }

    @Override
    protected String getEventKey() {
        return "pull_request_review_comment";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestReviewCommentEventDTO event) {
        var commentDto = event.comment();
        var prDto = event.pullRequest();

        if (commentDto == null || prDto == null) {
            logger.warn("Received pull_request_review_comment event with missing data");
            return;
        }

        logger.info(
            "Received pull_request_review_comment event: action={}, pr=#{}, comment={}, repo={}",
            event.action(),
            prDto.number(),
            commentDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        Long prId = prDto.getDatabaseId();

        // Delegate to processor based on action
        switch (event.actionType()) {
            case GitHubEventAction.PullRequestReviewComment.DELETED -> commentProcessor.processDeleted(
                commentDto.id(),
                prId,
                context
            );
            case GitHubEventAction.PullRequestReviewComment.CREATED -> commentProcessor.processCreated(
                commentDto,
                prId,
                context
            );
            case GitHubEventAction.PullRequestReviewComment.EDITED -> commentProcessor.processEdited(
                commentDto,
                prId,
                context
            );
            default -> logger.debug("Unhandled comment action: {}", event.action());
        }
    }
}

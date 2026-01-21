package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub pull_request_review webhook events.
 */
@Component
public class GitHubPullRequestReviewMessageHandler extends GitHubMessageHandler<GitHubPullRequestReviewEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final GitHubPullRequestReviewProcessor reviewProcessor;

    GitHubPullRequestReviewMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        GitHubPullRequestReviewProcessor reviewProcessor,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubPullRequestReviewEventDTO.class, deserializer, transactionTemplate);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.reviewProcessor = reviewProcessor;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PULL_REQUEST_REVIEW;
    }

    @Override
    protected void handleEvent(GitHubPullRequestReviewEventDTO event) {
        var reviewDto = event.review();
        var prDto = event.pullRequest();

        if (reviewDto == null || prDto == null) {
            log.warn("Received pull_request_review event with missing data: action={}", event.action());
            return;
        }

        log.info(
            "Received pull_request_review event: action={}, prNumber={}, reviewId={}, repoName={}",
            event.action(),
            prDto.number(),
            reviewDto.id(),
            event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Delegate to processor based on action
        if (event.actionType() == GitHubEventAction.PullRequestReview.DISMISSED) {
            reviewProcessor.processDismissed(reviewDto.id(), context);
        } else {
            reviewProcessor.process(reviewDto, prDto.getDatabaseId(), context);
        }
    }
}

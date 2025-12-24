package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookAction;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review webhook events.
 */
@Component
public class GitHubPullRequestReviewMessageHandler extends GitHubMessageHandler<GitHubPullRequestReviewEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final GitHubPullRequestReviewProcessor reviewProcessor;

    GitHubPullRequestReviewMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        GitHubPullRequestReviewProcessor reviewProcessor
    ) {
        super(GitHubPullRequestReviewEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.reviewProcessor = reviewProcessor;
    }

    @Override
    protected String getEventKey() {
        return "pull_request_review";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestReviewEventDTO event) {
        var reviewDto = event.review();
        var prDto = event.pullRequest();

        if (reviewDto == null || prDto == null) {
            logger.warn("Received pull_request_review event with missing data");
            return;
        }

        logger.info(
            "Received pull_request_review event: action={}, pr=#{}, review={}, repo={}",
            event.action(),
            prDto.number(),
            reviewDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Delegate to processor based on action
        if (event.isAction(GitHubWebhookAction.DISMISSED)) {
            reviewProcessor.processDismissed(reviewDto.id());
        } else {
            reviewProcessor.process(reviewDto, prDto.getDatabaseId());
        }
    }
}

package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto.GitHubPullRequestReviewThreadEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review_thread webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubPullRequestReviewThreadMessageHandler
    extends GitHubMessageHandler<GitHubPullRequestReviewThreadEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;

    GitHubPullRequestReviewThreadMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor
    ) {
        super(GitHubPullRequestReviewThreadEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
    }

    @Override
    protected String getEventKey() {
        return "pull_request_review_thread";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestReviewThreadEventDTO event) {
        var threadDto = event.thread();
        var prDto = event.pullRequest();

        if (threadDto == null || prDto == null) {
            logger.warn("Received pull_request_review_thread event with missing data");
            return;
        }

        logger.info(
            "Received pull_request_review_thread event: action={}, pr=#{}, thread={}, repo={}",
            event.action(),
            prDto.number(),
            threadDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // For now, just ensure PR is up to date
        // Thread handling can be expanded if needed
        prProcessor.process(prDto, context);

        // Log the thread action
        switch (event.action()) {
            case "resolved" -> logger.info("Thread {} resolved on PR #{}", threadDto.id(), prDto.number());
            case "unresolved" -> logger.info("Thread {} unresolved on PR #{}", threadDto.id(), prDto.number());
            default -> logger.debug("Unhandled thread action: {}", event.action());
        }
    }
}

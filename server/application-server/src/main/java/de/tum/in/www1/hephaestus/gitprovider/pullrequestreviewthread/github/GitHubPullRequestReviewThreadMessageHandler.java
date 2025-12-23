package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto.GitHubPullRequestReviewThreadEventDTO;
import java.time.Instant;
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
    private final PullRequestReviewThreadRepository threadRepository;

    GitHubPullRequestReviewThreadMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        PullRequestReviewThreadRepository threadRepository
    ) {
        super(GitHubPullRequestReviewThreadEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.threadRepository = threadRepository;
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

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Process thread state changes
        switch (event.action()) {
            case "resolved" -> processResolved(threadDto.id());
            case "unresolved" -> processUnresolved(threadDto.id());
            default -> logger.debug("Unhandled thread action: {}", event.action());
        }
    }

    private void processResolved(Long threadId) {
        if (threadId == null) {
            logger.warn("Cannot resolve thread: threadId is null");
            return;
        }
        threadRepository.findById(threadId).ifPresentOrElse(
            thread -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                thread.setResolvedAt(Instant.now());
                threadRepository.save(thread);
                logger.info("Thread {} resolved", threadId);
            },
            () -> logger.debug("Thread {} not found for resolve action, may not have been synced yet", threadId)
        );
    }

    private void processUnresolved(Long threadId) {
        if (threadId == null) {
            logger.warn("Cannot unresolve thread: threadId is null");
            return;
        }
        threadRepository.findById(threadId).ifPresentOrElse(
            thread -> {
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setResolvedAt(null);
                thread.setResolvedBy(null);
                threadRepository.save(thread);
                logger.info("Thread {} unresolved", threadId);
            },
            () -> logger.debug("Thread {} not found for unresolve action, may not have been synced yet", threadId)
        );
    }
}

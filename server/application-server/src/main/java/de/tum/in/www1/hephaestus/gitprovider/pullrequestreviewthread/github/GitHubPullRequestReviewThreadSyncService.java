package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing pull request review threads.
 * <p>
 * Note: Webhook event processing is now handled directly by
 * {@link GitHubPullRequestReviewThreadMessageHandler} using DTOs.
 * This service is retained for batch sync operations if needed.
 */
@Service
public class GitHubPullRequestReviewThreadSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadSyncService.class);

    private final PullRequestReviewThreadRepository threadRepository;

    public GitHubPullRequestReviewThreadSyncService(PullRequestReviewThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    /**
     * Resolves a thread by ID.
     *
     * @param threadId the thread ID
     */
    @Transactional
    public void resolveThread(Long threadId) {
        threadRepository
            .findById(threadId)
            .ifPresent(thread -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                threadRepository.save(thread);
                logger.info("Resolved thread {}", threadId);
            });
    }

    /**
     * Unresolves a thread by ID.
     *
     * @param threadId the thread ID
     */
    @Transactional
    public void unresolveThread(Long threadId) {
        threadRepository
            .findById(threadId)
            .ifPresent(thread -> {
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setResolvedAt(null);
                thread.setResolvedBy(null);
                threadRepository.save(thread);
                logger.info("Unresolved thread {}", threadId);
            });
    }
}

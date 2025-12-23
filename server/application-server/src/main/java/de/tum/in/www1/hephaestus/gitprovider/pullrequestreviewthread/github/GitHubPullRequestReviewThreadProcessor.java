package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request review threads.
 * <p>
 * This service handles state changes for pull request review threads,
 * including resolving and unresolving threads.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubPullRequestReviewThreadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadProcessor.class);

    private final PullRequestReviewThreadRepository threadRepository;

    public GitHubPullRequestReviewThreadProcessor(PullRequestReviewThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    /**
     * Resolve a thread by its ID.
     * Sets the thread state to RESOLVED and records the resolution time.
     *
     * @param threadId the thread ID
     * @return true if the thread was resolved, false if not found
     */
    @Transactional
    public boolean resolve(Long threadId) {
        if (threadId == null) {
            logger.warn("Cannot resolve thread: threadId is null");
            return false;
        }

        return threadRepository
            .findById(threadId)
            .map(thread -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                thread.setResolvedAt(Instant.now());
                threadRepository.save(thread);
                logger.info("Thread {} resolved", threadId);
                return true;
            })
            .orElseGet(() -> {
                logger.debug("Thread {} not found for resolve action, may not have been synced yet", threadId);
                return false;
            });
    }

    /**
     * Unresolve a thread by its ID.
     * Sets the thread state to UNRESOLVED and clears resolution metadata.
     *
     * @param threadId the thread ID
     * @return true if the thread was unresolved, false if not found
     */
    @Transactional
    public boolean unresolve(Long threadId) {
        if (threadId == null) {
            logger.warn("Cannot unresolve thread: threadId is null");
            return false;
        }

        return threadRepository
            .findById(threadId)
            .map(thread -> {
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setResolvedAt(null);
                thread.setResolvedBy(null);
                threadRepository.save(thread);
                logger.info("Thread {} unresolved", threadId);
                return true;
            })
            .orElseGet(() -> {
                logger.debug("Thread {} not found for unresolve action, may not have been synced yet", threadId);
                return false;
            });
    }
}

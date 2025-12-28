package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request review threads.
 */
@Service
public class GitHubPullRequestReviewThreadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewThreadProcessor.class);

    private final PullRequestReviewThreadRepository threadRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestReviewThreadProcessor(
        PullRequestReviewThreadRepository threadRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.threadRepository = threadRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Resolve without emitting events (for sync operations).
     */
    @Transactional
    public boolean resolve(Long threadId) {
        return resolve(threadId, null);
    }

    @Transactional
    public boolean resolve(Long threadId, ProcessingContext context) {
        if (threadId == null) {
            logger.warn("Cannot resolve thread: threadId is null");
            return false;
        }

        return threadRepository
            .findById(threadId)
            .map(thread -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                thread.setResolvedAt(Instant.now());
                thread = threadRepository.save(thread);
                if (context != null) {
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewThreadResolved(
                            EventPayload.ReviewThreadData.from(thread),
                            EventContext.from(context)
                        )
                    );
                }
                logger.info("Thread {} resolved", threadId);
                return true;
            })
            .orElseGet(() -> {
                logger.debug("Thread {} not found for resolve action, may not have been synced yet", threadId);
                return false;
            });
    }

    /**
     * Unresolve without emitting events (for sync operations).
     */
    @Transactional
    public boolean unresolve(Long threadId) {
        return unresolve(threadId, null);
    }

    @Transactional
    public boolean unresolve(Long threadId, ProcessingContext context) {
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
                thread = threadRepository.save(thread);
                if (context != null) {
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewThreadUnresolved(
                            EventPayload.ReviewThreadData.from(thread),
                            EventContext.from(context)
                        )
                    );
                }
                logger.info("Thread {} unresolved", threadId);
                return true;
            })
            .orElseGet(() -> {
                logger.debug("Thread {} not found for unresolve action, may not have been synced yet", threadId);
                return false;
            });
    }
}

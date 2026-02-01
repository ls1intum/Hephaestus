package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request review threads.
 */
@Service
public class GitHubPullRequestReviewThreadProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewThreadProcessor.class);

    private final PullRequestReviewThreadRepository threadRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestReviewThreadProcessor(
        PullRequestReviewThreadRepository threadRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.threadRepository = threadRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean resolve(Long threadId, User resolvedBy, @NonNull ProcessingContext context) {
        if (threadId == null) {
            log.debug("Skipped thread resolve: reason=nullThreadId");
            return false;
        }

        return threadRepository
            .findById(threadId)
            .map(thread -> {
                thread.setState(PullRequestReviewThread.State.RESOLVED);
                if (resolvedBy != null) {
                    thread.setResolvedBy(resolvedBy);
                }
                thread = threadRepository.save(thread);
                EventPayload.ReviewThreadData.from(thread).ifPresent(threadData ->
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewThreadResolved(threadData, EventContext.from(context))
                    )
                );
                log.info(
                    "Resolved thread: threadId={}, resolvedByLogin={}",
                    threadId,
                    resolvedBy != null ? resolvedBy.getLogin() : null
                );
                return true;
            })
            .orElseGet(() -> {
                log.debug("Skipped thread resolve: reason=threadNotFound, threadId={}", threadId);
                return false;
            });
    }

    @Transactional
    public boolean unresolve(Long threadId, @NonNull ProcessingContext context) {
        if (threadId == null) {
            log.debug("Skipped thread unresolve: reason=nullThreadId");
            return false;
        }

        return threadRepository
            .findById(threadId)
            .map(thread -> {
                thread.setState(PullRequestReviewThread.State.UNRESOLVED);
                thread.setResolvedBy(null);
                thread = threadRepository.save(thread);
                EventPayload.ReviewThreadData.from(thread).ifPresent(threadData ->
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewThreadUnresolved(threadData, EventContext.from(context))
                    )
                );
                log.info("Unresolved thread: threadId={}", threadId);
                return true;
            })
            .orElseGet(() -> {
                log.debug("Skipped thread unresolve: reason=threadNotFound, threadId={}", threadId);
                return false;
            });
    }
}

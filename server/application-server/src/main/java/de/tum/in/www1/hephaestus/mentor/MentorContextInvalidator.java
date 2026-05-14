package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts mentor aspect caches after committed domain events. Surgical point-key eviction
 * (per {@code workspaceId + ":" + userId} or per {@code workspaceId}) so a single CRUD does
 * not amplify into a thundering herd across active users.
 */
@Component
@RequiredArgsConstructor
public class MentorContextInvalidator {

    private static final Logger log = LoggerFactory.getLogger(MentorContextInvalidator.class);

    /** Caches scoped by {@code workspaceId + ":" + userId}. */
    private static final List<String> PER_USER_CACHES = List.of(
        "mentor_user_aspect",
        "mentor_workspace_aspect",
        "mentor_findings_aspect"
    );

    private final CacheManager cacheManager;
    private final WorkspaceRepository workspaceRepository;
    private final PullRequestRepository pullRequestRepository;

    /**
     * PR updates change the per-user activity counts: open PRs, merged-this-week, unresolved
     * threads, review states, etc. We invalidate per-user aspects for both the author and
     * (since the PR may have just been merged) the mergedBy user.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(DomainEvent.PullRequestUpdated event) {
        Long workspaceId = resolveWorkspaceId(event.context());
        if (workspaceId == null) return;
        Long authorId = event.pullRequest() != null ? event.pullRequest().authorId() : null;
        evictPerUser(workspaceId, authorId);
        // Also evict for the merger (if any) — their reviews-given/PRs-merged counters may
        // have changed even if they are not the PR author.
        Long mergedById = event.pullRequest() != null ? event.pullRequest().mergedById() : null;
        if (mergedById != null && !mergedById.equals(authorId)) {
            evictPerUser(workspaceId, mergedById);
        }
    }

    /**
     * Issue updates affect the assigned-work surface. Invalidate the workspace aspect for the
     * author (who sees their work list change).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUpdated(DomainEvent.IssueUpdated event) {
        Long workspaceId = resolveWorkspaceId(event.context());
        if (workspaceId == null) return;
        Long authorId = event.issue() != null ? event.issue().authorId() : null;
        evictPerUser(workspaceId, authorId);
    }

    /**
     * Review submissions change the reviewer's "reviews-given-this-week" counter AND, indirectly,
     * the PR author's "reviews-received-this-week" / "pending-review-requests" / "unresolved-threads"
     * counters. Without this, the user-aspect cache would lie for up to its TTL after every code
     * review — which is the highest-frequency event in the mentor's per-user surface.
     *
     * <p>{@code @Transactional(REQUIRES_NEW)} is mandatory: {@code AFTER_COMMIT} runs after the
     * originating transaction has closed, so the {@code findById} below needs its own session
     * to materialise {@code pr.getAuthor()} without a {@code LazyInitializationException}.
     * Pattern matches the rest of the codebase's {@code @TransactionalEventListener(AFTER_COMMIT)}
     * usages (e.g. {@code ActivityEventListener}).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        evictForReview(event.context(), event.review());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewEdited(DomainEvent.ReviewEdited event) {
        evictForReview(event.context(), event.review());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewDismissed(DomainEvent.ReviewDismissed event) {
        evictForReview(event.context(), event.review());
    }

    private void evictForReview(
        de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext context,
        de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload.ReviewData review
    ) {
        Long workspaceId = resolveWorkspaceId(context);
        if (workspaceId == null || review == null) return;
        evictPerUser(workspaceId, review.authorId());
        Long prAuthorId = pullRequestRepository
            .findById(review.pullRequestId())
            .map(pr -> pr.getAuthor() != null ? pr.getAuthor().getId() : null)
            .orElse(null);
        if (prAuthorId != null && !prAuthorId.equals(review.authorId())) {
            evictPerUser(workspaceId, prAuthorId);
        }
    }

    /**
     * Best-effort resolution of {@code workspaceId} from event context. Returns {@code null}
     * when the event lacks the necessary linkage — the caller is then a no-op.
     */
    private Long resolveWorkspaceId(de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext context) {
        if (context == null || context.repository() == null) return null;
        // The DomainEvent context carries a RepositoryRef — we resolve to workspace via the
        // RepositoryToMonitor join (same approach the aspect queries use).
        return workspaceRepository.findWorkspaceIdByRepositoryId(context.repository().id()).orElse(null);
    }

    private void evictPerUser(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) return;
        String key = workspaceId + ":" + userId;
        for (String cacheName : PER_USER_CACHES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Evicted {} key={}", cacheName, key);
            }
        }
    }
}

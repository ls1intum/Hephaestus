package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts mentor aspect caches after committed domain events. Surgical point-key eviction
 * (per {@code workspaceId + ":" + userId} or per {@code workspaceId}) so a single CRUD does
 * not amplify into a thundering herd across active users.
 *
 * <p>Every listener is {@link Async} so commit-callback latency does not block the publishing
 * transaction's caller. Matches the {@code ActivityEventListener} / {@code AchievementEventListener}
 * sibling pattern; ALL @Async work runs on the bounded {@code applicationTaskExecutor} configured
 * by {@code SpringAsyncConfig} (core 10 / max 50 / queue 500 / graceful shutdown), so a review-
 * event burst (label-spam, mass-approve) buffers cleanly without unbounded thread growth.
 */
@Component
@RequiredArgsConstructor
public class MentorContextInvalidator {

    private static final Logger log = LoggerFactory.getLogger(MentorContextInvalidator.class);

    /** Caches scoped by {@code workspaceId + ":" + userId}. */
    private static final List<String> PER_USER_CACHES = List.of(
        "mentor_user_aspect",
        "mentor_workspace_aspect",
        "mentor_findings_aspect",
        "mentor_practice_standing_aspect",
        // The authored-work aspect (RecentAuthoredWorkAspectProvider) is keyed per
        // workspaceId:developerId and goes stale on the same PR/issue/review events.
        "mentor_authored_work_aspect"
    );

    /**
     * Caches that depend on the developer's PERSISTED practice observations (findings + standing). They go
     * stale the moment a detection run writes new observations, independent of any SCM event — so they are
     * evicted on {@link PracticeDetectionCompletedEvent}, not on PR/issue/review updates. Delivered-feedback
     * eviction is deliberately NOT wired: there is no delivery event, and the delivered body is immutable
     * once posted (ADR 0021), so its cache cannot drift.
     */
    private static final List<String> DETECTION_DEPENDENT_CACHES = List.of(
        "mentor_findings_aspect",
        "mentor_practice_standing_aspect"
    );

    private final CacheManager cacheManager;
    private final WorkspaceRepository workspaceRepository;
    private final PullRequestRepository pullRequestRepository;

    /**
     * PR updates change the per-user activity counts: open PRs, merged-this-week, unresolved
     * threads, review states, etc. We invalidate per-user aspects for both the author and
     * (since the PR may have just been merged) the mergedBy user.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(ScmDomainEvent.PullRequestUpdated event) {
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
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUpdated(ScmDomainEvent.IssueUpdated event) {
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
     *
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewSubmitted(ScmDomainEvent.ReviewSubmitted event) {
        evictForReview(event.context(), event.review());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewEdited(ScmDomainEvent.ReviewEdited event) {
        evictForReview(event.context(), event.review());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReviewDismissed(ScmDomainEvent.ReviewDismissed event) {
        evictForReview(event.context(), event.review());
    }

    /**
     * A completed detection run persists new observations for this developer, staling the findings-history and
     * practice-standing aspects (they would otherwise lie until their TTL). Evict the two detection-dependent
     * per-user caches for the evaluated developer. The event carries only scalars, so no transaction is needed.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPracticeDetectionCompleted(
        de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent event
    ) {
        if (event == null || event.workspaceId() == null || event.developerId() == null) {
            return;
        }
        evictPerUser(event.workspaceId(), event.developerId(), DETECTION_DEPENDENT_CACHES);
    }

    private void evictForReview(
        de.tum.cit.aet.hephaestus.integration.core.events.EventContext context,
        de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload.ReviewData review
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
    private Long resolveWorkspaceId(de.tum.cit.aet.hephaestus.integration.core.events.EventContext context) {
        if (context == null || context.repository() == null) return null;
        // The ScmDomainEvent context carries a RepositoryRef — we resolve to workspace via the
        // RepositoryToMonitor join (same approach the aspect queries use).
        return workspaceRepository.findWorkspaceIdByRepositoryId(context.repository().id()).orElse(null);
    }

    private void evictPerUser(Long workspaceId, Long userId) {
        evictPerUser(workspaceId, userId, PER_USER_CACHES);
    }

    private void evictPerUser(Long workspaceId, Long userId, List<String> cacheNames) {
        if (workspaceId == null || userId == null) return;
        String key = workspaceId + ":" + userId;
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Evicted {} key={}", cacheName, key);
            }
        }
    }
}

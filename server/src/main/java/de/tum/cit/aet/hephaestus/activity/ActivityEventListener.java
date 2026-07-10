package de.tum.cit.aet.hephaestus.activity;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener service that translates domain events into activity ledger entries.
 *
 * <p>Listens for domain events (PR created, merged, review submitted, etc.) and
 * records them in the append-only activity event log.
 *
 * <p>Uses {@code @Async} to avoid blocking the main transaction and
 * {@code @TransactionalEventListener(AFTER_COMMIT)} to ensure events are only
 * recorded after successful domain operations.
 *
 * <p><b>Performance optimization:</b> Uses {@code getReferenceById()} to create
 * entity proxy references without database queries. The event payloads contain
 * all necessary data (IDs, timestamps), so we only need proxy references for
 * the {@code User} and {@code Repository} entities that are stored as foreign keys
 * in the activity event.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ActivityEventListener {

    private final ActivityEventService activityEventService;
    private final ActivityEventRepository activityEventRepository;
    private final PullRequestReviewThreadRepository reviewThreadRepository;
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;

    /**
     * Safely records an activity event, catching and logging any exceptions.
     * DRY helper to avoid repeated try-catch blocks in each event handler.
     */
    private void safeRecord(String eventName, Long entityId, Runnable recordAction) {
        try {
            recordAction.run();
        } catch (Exception e) {
            log.error("Failed to record activity event: eventType={}, entityId={}", eventName, entityId, e);
        }
    }

    /**
     * Validates that the event context has a valid scopeId.
     * Events without scopeId cannot be properly associated with a scope.
     *
     * @param eventName name of the event for logging
     * @param entityId  ID of the entity for logging
     * @param scopeId   the scopeId to validate
     * @return true if scopeId is valid, false otherwise
     */
    private boolean hasValidScopeId(String eventName, Long entityId, Long scopeId) {
        if (scopeId == null) {
            log.warn("Skipped event due to null scopeId: eventType={}, entityId={}", eventName, entityId);
            return false;
        }
        return true;
    }

    /**
     * Gets the actor (User) for an activity event, or null if the actor is unknown or doesn't exist.
     *
     * <p>When an author ID is null (e.g., GitHub user deleted, organization bot, etc.),
     * we return null rather than skipping the event. This preserves the activity record
     * for audit trail and repository metrics while leaving the actor unattributed.
     *
     * <p>Uses {@code findById()} to verify the user exists before returning a reference.
     * This prevents FK constraint violations when webhook events reference users
     * that haven't been synced to the local database (e.g., external collaborators,
     * deleted users, or users outside the monitored scope).
     *
     * <p>The ActivityEvent schema explicitly supports null actors for system events
     * and events where the original actor is no longer known.
     *
     * @param authorId the author ID from the event payload (nullable)
     * @return User reference if authorId is present and user exists, null otherwise
     */
    @Nullable
    private User getActorOrNull(@Nullable Long authorId) {
        if (authorId == null) {
            return null;
        }
        return userRepository.findById(authorId).orElse(null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(ScmDomainEvent.PullRequestCreated event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR created", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null || pr.createdAt() == null) {
            log.debug(
                "Skipping PR created event (author may be deleted): prId={}, authorId={}, createdAt={}",
                pr.id(),
                pr.authorId(),
                pr.createdAt()
            );
            return;
        }
        safeRecord("PR opened", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_OPENED,
                pr.createdAt(),
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestMerged(ScmDomainEvent.PullRequestMerged event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR merged", pr.id(), event.context().scopeId())) {
            return;
        }
        // For merged PRs, prefer mergedBy, fall back to author
        Long awardeeId = pr.mergedById() != null ? pr.mergedById() : pr.authorId();
        if (awardeeId == null) {
            log.debug("Skipping PR merged event (awardee may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.mergedAt() != null ? pr.mergedAt() : pr.updatedAt();
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        final Instant finalOccurredAt = occurredAt;
        safeRecord("PR merged", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_MERGED,
                finalOccurredAt,
                userRepository.getReferenceById(awardeeId),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(ScmDomainEvent.PullRequestClosed event) {
        if (event.wasMerged()) {
            return;
        }
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR closed", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR closed event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.closedAt() != null ? pr.closedAt() : pr.updatedAt();
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        final Instant finalOccurredAt = occurredAt;
        safeRecord("PR closed", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_CLOSED,
                finalOccurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(ScmDomainEvent.PullRequestReopened event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR reopened", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR reopened event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR reopened", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_REOPENED,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(ScmDomainEvent.PullRequestReady event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR ready", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR ready event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR ready", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_READY,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    /**
     * Handle pull request converted to draft (ready->draft transition).
     *
     * <p>Records a lifecycle event. Converting back to draft is a workflow
     * tracking event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestDrafted(ScmDomainEvent.PullRequestDrafted event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR drafted", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR drafted event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR drafted", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_DRAFTED,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    /**
     * Handle pull request synchronized (new commits pushed to the branch).
     *
     * <p>Records a lifecycle event. Pushing new commits is tracked for activity
     * completeness.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(ScmDomainEvent.PullRequestSynchronized event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR synchronized", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR synchronized event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR synchronized", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_SYNCHRONIZED,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    /**
     * Handle label added to pull request.
     *
     * <p>Records a workflow tracking event. Labeling is organizational activity
     * that helps with workflow.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(ScmDomainEvent.PullRequestLabeled event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR labeled", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR labeled event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR labeled", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_LABELED,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    /**
     * Handle label removed from pull request.
     *
     * <p>Records a workflow tracking event. Unlabeling is organizational activity
     * that helps with workflow.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUnlabeled(ScmDomainEvent.PullRequestUnlabeled event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR unlabeled", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.debug("Skipping PR unlabeled event (author may be deleted): prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR unlabeled", pr.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PULL_REQUEST_UNLABELED,
                occurredAt,
                userRepository.getReferenceById(pr.authorId()),
                repositoryRepository.getReferenceById(pr.repository().id()),
                ActivityTargetType.PULL_REQUEST,
                pr.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ScmDomainEvent.ReviewSubmitted event) {
        var reviewData = event.review();
        log.debug(
            "Received ReviewSubmitted event: reviewId={}, state={}, scopeId={}, authorId={}, repositoryId={}",
            reviewData.id(),
            reviewData.state(),
            event.context().scopeId(),
            reviewData.authorId(),
            reviewData.repositoryId()
        );
        if (!hasValidScopeId("Review submitted", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn(
                "Skipping review submitted event (author or repository may be deleted): reviewId={}, authorId={}, repositoryId={}",
                reviewData.id(),
                reviewData.authorId(),
                reviewData.repositoryId()
            );
            return;
        }
        Instant occurredAt = reviewData.submittedAt() != null ? reviewData.submittedAt() : Instant.now();
        safeRecord("review", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                mapReviewState(reviewData.state()),
                occurredAt,
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewDismissed(ScmDomainEvent.ReviewDismissed event) {
        var reviewData = event.review();
        if (!hasValidScopeId("Review dismissed", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.debug(
                "Skipping review dismissed event (author may be deleted): reviewId={}, authorId={}, repositoryId={}",
                reviewData.id(),
                reviewData.authorId(),
                reviewData.repositoryId()
            );
            return;
        }
        // Record a REVIEW_DISMISSED event — dismissed reviews still count for the activity aggregation.
        safeRecord("review dismissed", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_DISMISSED,
                Instant.now(), // Use current time for dismissal
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id()
            )
        );
    }

    /**
     * Handle review edited events.
     *
     * <p>When a review is edited (e.g., body text changes), we record a new event
     * for audit-trail purposes. The original REVIEW_SUBMITTED event already
     * captured the review.
     *
     * <p>Note: This creates a new event rather than updating the original,
     * maintaining an immutable audit trail of all review activity.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewEdited(ScmDomainEvent.ReviewEdited event) {
        var reviewData = event.review();
        if (!hasValidScopeId("Review edited", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.debug(
                "Skipping review edited event (author may be deleted): reviewId={}, authorId={}, repositoryId={}",
                reviewData.id(),
                reviewData.authorId(),
                reviewData.repositoryId()
            );
            return;
        }
        Instant occurredAt = reviewData.submittedAt() != null ? reviewData.submittedAt() : Instant.now();
        safeRecord("review edited", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_EDITED,
                occurredAt,
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(ScmDomainEvent.CommentCreated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Comment created", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping comment created event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        safeRecord("comment", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.ISSUE_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle comment updated events.
     *
     * <p>Records an audit-trail event. The original comment creation already
     * captured the comment.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentUpdated(ScmDomainEvent.CommentUpdated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Comment updated", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping comment updated event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = Instant.now(); // Use current time for edits
        safeRecord("comment updated", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.COMMENT_UPDATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.ISSUE_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle comment deleted events.
     *
     * <p>Records an audit-trail event. Note that we may not have full comment
     * data since the entity was deleted - we rely on the event metadata.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentDeleted(ScmDomainEvent.CommentDeleted event) {
        Long commentId = event.commentId();
        if (!hasValidScopeId("Comment deleted", commentId, event.context().scopeId())) {
            return;
        }
        // For deleted comments, we don't have author info from the event
        // We record with a null user reference - this is acceptable for audit trail
        log.debug("Recording comment deleted event: commentId={}", commentId);
        safeRecord("comment deleted", commentId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.COMMENT_DELETED,
                Instant.now(),
                ActivityTargetType.ISSUE_COMMENT,
                commentId
            )
        );
    }

    /**
     * Handle review comment (inline code comment) created events.
     *
     * <p>Covers both comments linked to a review (GitHub-style) and standalone
     * comments without a parent review (GitLab diff notes).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentCreated(ScmDomainEvent.ReviewCommentCreated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Review comment created", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping review comment created event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        safeRecord("review comment", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.REVIEW_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle review comment edited events.
     *
     * <p>Records an audit-trail event. Edits are tracked for completeness.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentEdited(ScmDomainEvent.ReviewCommentEdited event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Review comment edited", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping review comment edited event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = Instant.now(); // Use current time for edits
        safeRecord("review comment edited", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_COMMENT_EDITED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.REVIEW_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle review comment deleted events.
     *
     * <p>Records an audit-trail event. Note that we may not have full comment
     * data since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentDeleted(ScmDomainEvent.ReviewCommentDeleted event) {
        Long commentId = event.commentId();
        if (!hasValidScopeId("Review comment deleted", commentId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording review comment deleted event: commentId={}", commentId);
        safeRecord("review comment deleted", commentId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.REVIEW_COMMENT_DELETED,
                Instant.now(),
                ActivityTargetType.REVIEW_COMMENT,
                commentId
            )
        );
    }

    // Review Thread Events (Code Review Effectiveness)

    /**
     * Handle review thread resolved events.
     *
     * <p>Resolving a review thread indicates that code review feedback has been
     * addressed. This is valuable for tracking code review effectiveness metrics.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewThreadResolved(ScmDomainEvent.ReviewThreadResolved event) {
        var threadData = event.thread();
        if (!hasValidScopeId("Review thread resolved", threadData.id(), event.context().scopeId())) {
            return;
        }
        // Fetch the thread to get repository and resolver info
        PullRequestReviewThread thread = reviewThreadRepository.findById(threadData.id()).orElse(null);
        if (thread == null) {
            log.debug("Skipping review thread resolved event (thread not found): threadId={}", threadData.id());
            return;
        }
        if (thread.getPullRequest() == null || thread.getPullRequest().getRepository() == null) {
            log.debug("Skipping review thread resolved event (missing PR or repository): threadId={}", threadData.id());
            return;
        }
        // The resolver might be different from the PR author - use resolvedBy if available
        Long resolverId =
            thread.getResolvedBy() != null
                ? thread.getResolvedBy().getId()
                : (thread.getPullRequest().getAuthor() != null ? thread.getPullRequest().getAuthor().getId() : null);
        if (resolverId == null) {
            log.debug("Skipping review thread resolved event (no resolver found): threadId={}", threadData.id());
            return;
        }
        Long repositoryId = thread.getPullRequest().getRepository().getId();
        safeRecord("review thread resolved", threadData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_THREAD_RESOLVED,
                Instant.now(),
                userRepository.getReferenceById(resolverId),
                repositoryRepository.getReferenceById(repositoryId),
                ActivityTargetType.REVIEW_THREAD,
                threadData.id()
            )
        );
    }

    /**
     * Handle review thread unresolved events.
     *
     * <p>Unresolving a review thread indicates that previously addressed feedback
     * needs more attention. This is workflow tracking.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewThreadUnresolved(ScmDomainEvent.ReviewThreadUnresolved event) {
        var threadData = event.thread();
        if (!hasValidScopeId("Review thread unresolved", threadData.id(), event.context().scopeId())) {
            return;
        }
        // Fetch the thread to get repository info
        PullRequestReviewThread thread = reviewThreadRepository.findById(threadData.id()).orElse(null);
        if (thread == null) {
            log.debug("Skipping review thread unresolved event (thread not found): threadId={}", threadData.id());
            return;
        }
        if (thread.getPullRequest() == null || thread.getPullRequest().getRepository() == null) {
            log.debug(
                "Skipping review thread unresolved event (missing PR or repository): threadId={}",
                threadData.id()
            );
            return;
        }
        // For unresolved, we use the PR author since we don't track who unresolved it
        Long userId = thread.getPullRequest().getAuthor() != null ? thread.getPullRequest().getAuthor().getId() : null;
        if (userId == null) {
            log.debug("Skipping review thread unresolved event (no user found): threadId={}", threadData.id());
            return;
        }
        Long repositoryId = thread.getPullRequest().getRepository().getId();
        safeRecord("review thread unresolved", threadData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_THREAD_UNRESOLVED,
                Instant.now(),
                userRepository.getReferenceById(userId),
                repositoryRepository.getReferenceById(repositoryId),
                ActivityTargetType.REVIEW_THREAD,
                threadData.id()
            )
        );
    }

    /**
     * Handle issue created events.
     *
     * <p>Records ISSUE_CREATED activity event. If the author is unknown (null),
     * the event is still recorded for audit purposes with no attributed actor.
     * This handles cases where the GitHub user was deleted or the issue was
     * created by a bot.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(ScmDomainEvent.IssueCreated event) {
        var issueData = event.issue();
        // Skip pull requests - they have their own event (PULL_REQUEST_OPENED)
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue created", issueData.id(), event.context().scopeId())) {
            return;
        }
        User actor = getActorOrNull(issueData.authorId());
        // Log unknown authors for observability, but don't skip the event
        if (actor == null) {
            log.info(
                "Recording issue created event with unknown author (user deleted or bot): issueId={}",
                issueData.id()
            );
        }
        Instant occurredAt = issueData.createdAt() != null ? issueData.createdAt() : Instant.now();
        safeRecord("issue created", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_CREATED,
                occurredAt,
                actor,
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle issue closed events.
     *
     * <p>Records a lifecycle event. Issue closure is tracked for activity
     * completeness.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueClosed(ScmDomainEvent.IssueClosed event) {
        var issueData = event.issue();
        // Skip pull requests - they have their own events
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue closed", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.closedAt() != null ? issueData.closedAt() : issueData.updatedAt();
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        final Instant finalOccurredAt = occurredAt;
        safeRecord("issue closed", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_CLOSED,
                finalOccurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle issue reopened events.
     *
     * <p>Records a lifecycle event. Reopening an issue is workflow tracking
     * indicating work resumption.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueReopened(ScmDomainEvent.IssueReopened event) {
        var issueData = event.issue();
        // Skip pull requests - they have their own events
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue reopened", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.updatedAt() != null ? issueData.updatedAt() : Instant.now();
        safeRecord("issue reopened", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_REOPENED,
                occurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle issue deleted events.
     *
     * <p>Records an audit-trail event. Note that we only have the issue ID
     * since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueDeleted(ScmDomainEvent.IssueDeleted event) {
        Long issueId = event.issueId();
        if (!hasValidScopeId("Issue deleted", issueId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording issue deleted event: issueId={}", issueId);
        safeRecord("issue deleted", issueId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.ISSUE_DELETED,
                Instant.now(),
                ActivityTargetType.ISSUE,
                issueId
            )
        );
    }

    /**
     * Handle label added to issue events.
     *
     * <p>Records a workflow tracking event. Labeling is organizational activity
     * that helps with categorization.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueLabeled(ScmDomainEvent.IssueLabeled event) {
        var issueData = event.issue();
        // Skip pull requests - they have their own events
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue labeled", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.updatedAt() != null ? issueData.updatedAt() : Instant.now();
        safeRecord("issue labeled", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_LABELED,
                occurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle label removed from issue events.
     *
     * <p>Records a workflow tracking event. Unlabeling is organizational activity
     * that helps with categorization.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUnlabeled(ScmDomainEvent.IssueUnlabeled event) {
        var issueData = event.issue();
        // Skip pull requests - they have their own events
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue unlabeled", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.updatedAt() != null ? issueData.updatedAt() : Instant.now();
        safeRecord("issue unlabeled", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_UNLABELED,
                occurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle issue type assigned events.
     *
     * <p>Records a workflow tracking event. Assigning issue types
     * (bug, feature, task, etc.) is categorization that helps with work tracking.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueTyped(ScmDomainEvent.IssueTyped event) {
        var issueData = event.issue();
        // Skip pull requests
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue typed", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.updatedAt() != null ? issueData.updatedAt() : Instant.now();
        safeRecord("issue typed", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_TYPED,
                occurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    /**
     * Handle issue type removed events.
     *
     * <p>Records a workflow tracking event. Removing issue types
     * is a categorization change for work tracking purposes.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUntyped(ScmDomainEvent.IssueUntyped event) {
        var issueData = event.issue();
        // Skip pull requests
        if (issueData.isPullRequest()) {
            return;
        }
        if (!hasValidScopeId("Issue untyped", issueData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = issueData.updatedAt() != null ? issueData.updatedAt() : Instant.now();
        safeRecord("issue untyped", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_UNTYPED,
                occurredAt,
                getActorOrNull(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id()
            )
        );
    }

    private ActivityEventType mapReviewState(PullRequestReview.State state) {
        if (state == PullRequestReview.State.APPROVED) {
            return ActivityEventType.REVIEW_APPROVED;
        }
        if (state == PullRequestReview.State.CHANGES_REQUESTED) {
            return ActivityEventType.REVIEW_CHANGES_REQUESTED;
        }
        if (state == PullRequestReview.State.UNKNOWN) {
            return ActivityEventType.REVIEW_UNKNOWN;
        }
        return ActivityEventType.REVIEW_COMMENTED;
    }

    // Commit Events

    /**
     * Handle commit created events.
     *
     * <p>Records COMMIT_CREATED activity event. If the author is unknown (null),
     * the event is still recorded for audit purposes with no attributed actor.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitCreated(ScmDomainEvent.CommitCreated event) {
        var commitData = event.commit();
        if (!hasValidScopeId("Commit created", commitData.id(), event.context().scopeId())) {
            return;
        }
        User actor = getActorOrNull(commitData.authorId());
        Instant occurredAt = commitData.authoredAt();
        safeRecord("commit created", commitData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.COMMIT_CREATED,
                occurredAt,
                actor,
                repositoryRepository.getReferenceById(commitData.repositoryId()),
                ActivityTargetType.COMMIT,
                commitData.id()
            )
        );
    }

    /**
     * Handle commit author reconciliation events emitted by the integration.scm module
     * after commit author identities have been resolved (via email lookup, provider
     * user API, or server-side author harvest) for a repository.
     *
     * <p>COMMIT_CREATED activity events ingested before author resolution were
     * recorded with {@code actor_id=NULL}. This handler rewrites those ledger rows so
     * the newly attributed contributor appears on the activity aggregation. Scoped
     * per-repository to keep the UPDATE bounded.
     *
     * <p>Uses {@link EventListener} (not {@code @TransactionalEventListener}) because
     * the publishers ({@code CommitAuthorEnrichmentService}, {@code GitLabCommitMergeRequestLinker})
     * run bulk UPDATEs that auto-commit per statement outside a surrounding transaction.
     * {@code AFTER_COMMIT} would silently drop the event when no transaction is active.
     * The underlying {@code backfillCommitActors} UPDATE is idempotent (guarded by
     * {@code actor_id IS NULL}), so replay safety is preserved.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @EventListener
    public void onCommitAuthorsReconciled(ScmDomainEvent.CommitAuthorsReconciled event) {
        Long repositoryId = event.repositoryId();
        if (repositoryId == null) {
            return;
        }
        String correlationId = event.context() != null ? event.context().correlationId() : null;
        try {
            int updated = activityEventRepository.backfillCommitActors(repositoryId);
            log.info(
                "Backfilled {} COMMIT_CREATED activity events: repoId={}, correlationId={}",
                updated,
                repositoryId,
                correlationId
            );
        } catch (Exception e) {
            log.error("Failed to backfill commit actors: repoId={}, correlationId={}", repositoryId, correlationId, e);
        }
    }

    // Discussion Events (Community Engagement Tracking)

    /**
     * Handle discussion created events.
     *
     * <p>Records DISCUSSION_CREATED activity event. Discussions are a community
     * engagement signal, tracked for activity completeness.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionCreated(ScmDomainEvent.DiscussionCreated event) {
        var discussion = event.discussion();
        if (!hasValidScopeId("Discussion created", discussion.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = discussion.createdAt() != null ? discussion.createdAt() : Instant.now();
        User actor = getActorOrNull(discussion.authorId());
        safeRecord("discussion created", discussion.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_CREATED,
                occurredAt,
                actor,
                repositoryRepository.getReferenceById(discussion.repository().id()),
                ActivityTargetType.DISCUSSION,
                discussion.id()
            )
        );
    }

    /**
     * Handle discussion closed events.
     *
     * <p>Records DISCUSSION_CLOSED activity event. Closing a discussion
     * is lifecycle tracking.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionClosed(ScmDomainEvent.DiscussionClosed event) {
        var discussion = event.discussion();
        if (!hasValidScopeId("Discussion closed", discussion.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            discussion.closedAt() != null
                ? discussion.closedAt()
                : discussion.updatedAt() != null
                    ? discussion.updatedAt()
                    : Instant.now();
        safeRecord("discussion closed", discussion.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_CLOSED,
                occurredAt,
                getActorOrNull(discussion.authorId()),
                repositoryRepository.getReferenceById(discussion.repository().id()),
                ActivityTargetType.DISCUSSION,
                discussion.id()
            )
        );
    }

    /**
     * Handle discussion reopened events.
     *
     * <p>Records DISCUSSION_REOPENED activity event. Reopening is
     * lifecycle tracking indicating resumed community engagement.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionReopened(ScmDomainEvent.DiscussionReopened event) {
        var discussion = event.discussion();
        if (!hasValidScopeId("Discussion reopened", discussion.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = discussion.updatedAt() != null ? discussion.updatedAt() : Instant.now();
        safeRecord("discussion reopened", discussion.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_REOPENED,
                occurredAt,
                getActorOrNull(discussion.authorId()),
                repositoryRepository.getReferenceById(discussion.repository().id()),
                ActivityTargetType.DISCUSSION,
                discussion.id()
            )
        );
    }

    /**
     * Handle discussion answered events.
     *
     * <p>Records DISCUSSION_ANSWERED activity event. Having a discussion answered
     * is a valuable community engagement signal, indicating that the discussion
     * author's question was resolved.
     *
     * <p>The event is attributed to the discussion author (the person who asked
     * the question) when an answer is chosen. The actual answerer would be
     * tracked via discussion comment events (future enhancement).
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionAnswered(ScmDomainEvent.DiscussionAnswered event) {
        var discussion = event.discussion();
        if (!hasValidScopeId("Discussion answered", discussion.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            discussion.answerChosenAt() != null
                ? discussion.answerChosenAt()
                : discussion.updatedAt() != null
                    ? discussion.updatedAt()
                    : Instant.now();
        User actor = getActorOrNull(discussion.authorId());
        safeRecord("discussion answered", discussion.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_ANSWERED,
                occurredAt,
                actor,
                repositoryRepository.getReferenceById(discussion.repository().id()),
                ActivityTargetType.DISCUSSION,
                discussion.id()
            )
        );
    }

    /**
     * Handle discussion deleted events.
     *
     * <p>Records an audit-trail event. Note that we only have the
     * discussion ID since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionDeleted(ScmDomainEvent.DiscussionDeleted event) {
        Long discussionId = event.discussionId();
        if (!hasValidScopeId("Discussion deleted", discussionId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording discussion deleted event: discussionId={}", discussionId);
        safeRecord("discussion deleted", discussionId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_DELETED,
                Instant.now(),
                ActivityTargetType.DISCUSSION,
                discussionId
            )
        );
    }

    // Discussion Comment Events

    /**
     * Handle discussion comment created events.
     *
     * <p>Records DISCUSSION_COMMENT_CREATED activity event. Discussion comments
     * are a community engagement signal, tracked for activity.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionCommentCreated(ScmDomainEvent.DiscussionCommentCreated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Discussion comment created", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping discussion comment created event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        safeRecord("discussion comment created", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.DISCUSSION_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle discussion comment edited events.
     *
     * <p>Records an audit-trail event. The original comment creation already
     * captured the comment.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionCommentEdited(ScmDomainEvent.DiscussionCommentEdited event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Discussion comment edited", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.debug(
                "Skipping discussion comment edited event (author may be deleted): commentId={}, authorId={}, repositoryId={}",
                commentData.id(),
                commentData.authorId(),
                commentData.repositoryId()
            );
            return;
        }
        Instant occurredAt = Instant.now(); // Use current time for edits
        safeRecord("discussion comment edited", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_COMMENT_EDITED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.DISCUSSION_COMMENT,
                commentData.id()
            )
        );
    }

    /**
     * Handle discussion comment deleted events.
     *
     * <p>Records an audit-trail event. Note that we may not have full comment
     * data since the entity was deleted - we rely on the event metadata.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionCommentDeleted(ScmDomainEvent.DiscussionCommentDeleted event) {
        Long commentId = event.commentId();
        if (!hasValidScopeId("Discussion comment deleted", commentId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording discussion comment deleted event: commentId={}", commentId);
        safeRecord("discussion comment deleted", commentId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.DISCUSSION_COMMENT_DELETED,
                Instant.now(),
                ActivityTargetType.DISCUSSION_COMMENT,
                commentId
            )
        );
    }
}

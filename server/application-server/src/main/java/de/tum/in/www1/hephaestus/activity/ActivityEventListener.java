package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
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
 * records them in the activity event log with calculated XP values.
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
    private final ExperiencePointCalculator xpCalc;
    private final PullRequestReviewRepository reviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final PullRequestReviewThreadRepository reviewThreadRepository;
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final ProjectRepository projectRepository;
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
     * for audit trail and repository metrics while correctly attributing no XP.
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

    /**
     * Calculates XP for an event, returning 0 if the actor is unknown.
     *
     * <p>When we don't know who performed an action (author deleted, bot, etc.),
     * no XP should be awarded. The event is still recorded for audit purposes,
     * but deleted users cannot earn XP posthumously.
     *
     * @param actor the actor user (nullable)
     * @param xpIfKnown the XP to award if the actor is known
     * @return xpIfKnown if actor is present, 0.0 otherwise
     */
    private double xpForActor(@Nullable User actor, double xpIfKnown) {
        return actor != null ? xpIfKnown : 0.0;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
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
                pr.id(),
                xpCalc.getXpPullRequestOpened()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestMerged(DomainEvent.PullRequestMerged event) {
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
                pr.id(),
                xpCalc.getXpPullRequestMerged()
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(DomainEvent.PullRequestClosed event) {
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
                pr.id(),
                0.0
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(DomainEvent.PullRequestReopened event) {
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
                pr.id(),
                0.0 // Reopening is lifecycle tracking, no XP reward
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
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
                pr.id(),
                xpCalc.getXpPullRequestReady()
            )
        );
    }

    /**
     * Handle pull request converted to draft (ready->draft transition).
     *
     * <p>Records lifecycle event with 0 XP - converting back to draft is
     * a workflow tracking event, not a value-adding activity.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestDrafted(DomainEvent.PullRequestDrafted event) {
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
                pr.id(),
                0.0 // Draft conversion is lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle pull request synchronized (new commits pushed to the branch).
     *
     * <p>Records lifecycle event with 0 XP - pushing new commits is tracked
     * for activity completeness but doesn't award XP (the PR creation already did).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
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
                pr.id(),
                0.0 // Synchronization is lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle label added to pull request.
     *
     * <p>Records workflow tracking event with 0 XP - labeling is organizational
     * activity that helps with workflow but doesn't directly add value.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(DomainEvent.PullRequestLabeled event) {
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
                pr.id(),
                0.0 // Labeling is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle label removed from pull request.
     *
     * <p>Records workflow tracking event with 0 XP - unlabeling is organizational
     * activity that helps with workflow but doesn't directly add value.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUnlabeled(DomainEvent.PullRequestUnlabeled event) {
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
                pr.id(),
                0.0 // Unlabeling is workflow tracking, no XP reward
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        var reviewData = event.review();
        log.info(
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
        // Calculate XP for THIS single review only - not cumulative across all reviews.
        // Each event stores XP for its own review to avoid double-counting when aggregated.
        PullRequestReview review = reviewRepository.findById(reviewData.id()).orElse(null);
        if (review == null) {
            log.warn("Review not found for XP calculation: reviewId={}", reviewData.id());
            return;
        }
        double xp = xpCalc.calculateReviewExperiencePoints(review);
        Instant occurredAt = reviewData.submittedAt() != null ? reviewData.submittedAt() : Instant.now();
        safeRecord("review", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                mapReviewState(reviewData.state()),
                occurredAt,
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id(),
                xp
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewDismissed(DomainEvent.ReviewDismissed event) {
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
        // Record a REVIEW_DISMISSED event with 0 XP - dismissals don't affect XP
        // since dismissed reviews still count for the leaderboard
        double xpAdjustment = 0.0;

        safeRecord("review dismissed", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_DISMISSED,
                Instant.now(), // Use current time for dismissal
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id(),
                xpAdjustment
            )
        );
    }

    /**
     * Handle review edited events.
     *
     * <p>When a review is edited (e.g., body text changes), we record a new event
     * for audit trail purposes with 0 XP. The original REVIEW_SUBMITTED event
     * already captured the XP, so edited events should not add more XP to avoid
     * double-counting in leaderboard aggregation.
     *
     * <p>Note: This creates a new event rather than updating the original,
     * maintaining an immutable audit trail of all review activity.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewEdited(DomainEvent.ReviewEdited event) {
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
        // Record with 0 XP - the original review submission already captured XP.
        // Editing a review should not grant additional XP to avoid double-counting.
        Instant occurredAt = reviewData.submittedAt() != null ? reviewData.submittedAt() : Instant.now();
        safeRecord("review edited", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_EDITED,
                occurredAt,
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id(),
                0.0 // No XP for edits - original submission already counted
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(DomainEvent.CommentCreated event) {
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
        // Fetch the full IssueComment entity to calculate complexity-weighted XP
        IssueComment issueComment = issueCommentRepository.findById(commentData.id()).orElse(null);
        double xp;
        if (issueComment != null) {
            xp = xpCalc.calculateIssueCommentExperiencePoints(issueComment);
        } else {
            log.warn("IssueComment not found for XP calculation, using fallback: commentId={}", commentData.id());
            xp = xpCalc.getXpReviewComment();
        }
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        final double finalXp = xp;
        safeRecord("comment", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.ISSUE_COMMENT,
                commentData.id(),
                finalXp
            )
        );
    }

    /**
     * Handle comment updated events.
     *
     * <p>Records audit trail event with 0 XP - the original comment creation
     * already captured XP, edits don't add additional value.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentUpdated(DomainEvent.CommentUpdated event) {
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
                commentData.id(),
                0.0 // No XP for edits - original creation already counted
            )
        );
    }

    /**
     * Handle comment deleted events.
     *
     * <p>Records audit trail event with 0 XP. Note that we may not have full
     * comment data since the entity was deleted - we rely on the event metadata.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentDeleted(DomainEvent.CommentDeleted event) {
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
     * <p>Records with 0 XP because the review's XP calculation already factors in
     * the number of inline comments via {@code calculateCodeReviewBonus()}. Adding
     * separate XP here would double-count the same comments.
     *
     * <p>We still record the event for audit trail and activity tracking purposes.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentCreated(DomainEvent.ReviewCommentCreated event) {
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
        // Record with 0 XP - the review's calculateCodeReviewBonus() already factors in comment count.
        // Recording the event for audit purposes only.
        safeRecord("review comment", commentData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.REVIEW_COMMENT,
                commentData.id(),
                0.0 // No XP - already counted in review's code review bonus
            )
        );
    }

    /**
     * Handle review comment edited events.
     *
     * <p>Records audit trail event with 0 XP - edits are tracked for completeness
     * but don't affect XP since the review bonus already accounted for the comment.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentEdited(DomainEvent.ReviewCommentEdited event) {
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
                commentData.id(),
                0.0 // No XP for edits
            )
        );
    }

    /**
     * Handle review comment deleted events.
     *
     * <p>Records audit trail event with 0 XP. Note that we may not have full
     * comment data since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentDeleted(DomainEvent.ReviewCommentDeleted event) {
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

    // ========================================================================
    // Review Thread Events (Code Review Effectiveness)
    // ========================================================================

    /**
     * Handle review thread resolved events.
     *
     * <p>Resolving a review thread indicates that code review feedback has been
     * addressed. This is valuable for tracking code review effectiveness metrics.
     * Records with 0 XP since the value is in the resolution of feedback, not
     * in the act of marking it resolved.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewThreadResolved(DomainEvent.ReviewThreadResolved event) {
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
                threadData.id(),
                0.0 // Lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle review thread unresolved events.
     *
     * <p>Unresolving a review thread indicates that previously addressed feedback
     * needs more attention. Records with 0 XP since this is workflow tracking.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewThreadUnresolved(DomainEvent.ReviewThreadUnresolved event) {
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
                threadData.id(),
                0.0 // Lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle issue created events.
     *
     * <p>Records ISSUE_CREATED activity event. If the author is unknown (null),
     * the event is still recorded for audit purposes but with 0 XP. This handles
     * cases where the GitHub user was deleted or the issue was created by a bot.
     *
     * <p>XP is only awarded when we can attribute the action to a known user.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(DomainEvent.IssueCreated event) {
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
                "Recording issue created event with unknown author (user deleted or bot): issueId={}, xp=0",
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
                issueData.id(),
                xpForActor(actor, xpCalc.getXpIssueCreated())
            )
        );
    }

    /**
     * Handle issue closed events.
     *
     * <p>Records lifecycle event with 0 XP - issue closure is tracked for
     * activity completeness but doesn't award XP.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueClosed(DomainEvent.IssueClosed event) {
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
                issueData.id(),
                0.0 // Issue closure is lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle issue reopened events.
     *
     * <p>Records lifecycle event with 0 XP - reopening an issue is workflow
     * tracking indicating work resumption.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueReopened(DomainEvent.IssueReopened event) {
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
                issueData.id(),
                0.0 // Reopening is lifecycle tracking, no XP reward
            )
        );
    }

    /**
     * Handle issue deleted events.
     *
     * <p>Records audit trail event with 0 XP. Note that we only have the issue ID
     * since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueDeleted(DomainEvent.IssueDeleted event) {
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
     * <p>Records workflow tracking event with 0 XP - labeling is organizational
     * activity that helps with categorization but doesn't directly add value.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueLabeled(DomainEvent.IssueLabeled event) {
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
                issueData.id(),
                0.0 // Labeling is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle label removed from issue events.
     *
     * <p>Records workflow tracking event with 0 XP - unlabeling is organizational
     * activity that helps with categorization but doesn't directly add value.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUnlabeled(DomainEvent.IssueUnlabeled event) {
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
                issueData.id(),
                0.0 // Unlabeling is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle issue type assigned events.
     *
     * <p>Records workflow tracking event with 0 XP - assigning issue types
     * (bug, feature, task, etc.) is categorization that helps with work tracking.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueTyped(DomainEvent.IssueTyped event) {
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
                issueData.id(),
                0.0 // Type assignment is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle issue type removed events.
     *
     * <p>Records workflow tracking event with 0 XP - removing issue types
     * is a categorization change for work tracking purposes.
     *
     * <p>Events are recorded even when author is unknown (null actor).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueUntyped(DomainEvent.IssueUntyped event) {
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
                issueData.id(),
                0.0 // Type removal is workflow tracking, no XP reward
            )
        );
    }

    // ========================================================================
    // Project Events (Project Management Tracking)
    // ========================================================================

    /**
     * Handle project created events.
     *
     * <p>Records PROJECT_CREATED activity event with 0 XP. Project creation is
     * tracked for activity completeness and audit trail purposes.
     *
     * <p>The actor is the project creator if available.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCreated(DomainEvent.ProjectCreated event) {
        var projectData = event.project();
        if (!hasValidScopeId("Project created", projectData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            projectData.createdAt() != null
                ? projectData.createdAt()
                : projectData.updatedAt() != null
                    ? projectData.updatedAt()
                    : Instant.now();
        safeRecord("project created", projectData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_CREATED,
                occurredAt,
                getActorOrNull(projectData.creatorId()),
                getRepositoryForProject(projectData),
                ActivityTargetType.PROJECT,
                projectData.id(),
                0.0 // Project creation is tracked without XP
            )
        );
    }

    /**
     * Handle project updated events.
     *
     * <p>Records PROJECT_UPDATED activity event with 0 XP. Updates are tracked
     * for audit trail purposes.
     *
     * <p>The actor is the user who performed the update (from webhook sender),
     * falling back to the project creator for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectUpdated(DomainEvent.ProjectUpdated event) {
        var projectData = event.project();
        if (!hasValidScopeId("Project updated", projectData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = projectData.updatedAt() != null ? projectData.updatedAt() : Instant.now();
        // Use actorId (webhook sender) if available, fall back to creatorId for sync
        Long actorId = projectData.actorId() != null ? projectData.actorId() : projectData.creatorId();
        safeRecord("project updated", projectData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_UPDATED,
                occurredAt,
                getActorOrNull(actorId),
                getRepositoryForProject(projectData),
                ActivityTargetType.PROJECT,
                projectData.id(),
                0.0 // Project updates are workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project closed events.
     *
     * <p>Records PROJECT_CLOSED activity event with 0 XP. Closing a project
     * indicates completion or archival.
     *
     * <p>The actor is the user who closed the project (from webhook sender),
     * falling back to the project creator for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectClosed(DomainEvent.ProjectClosed event) {
        var projectData = event.project();
        if (!hasValidScopeId("Project closed", projectData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            projectData.closedAt() != null
                ? projectData.closedAt()
                : projectData.updatedAt() != null
                    ? projectData.updatedAt()
                    : Instant.now();
        // Use actorId (webhook sender) if available, fall back to creatorId for sync
        Long actorId = projectData.actorId() != null ? projectData.actorId() : projectData.creatorId();
        safeRecord("project closed", projectData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_CLOSED,
                occurredAt,
                getActorOrNull(actorId),
                getRepositoryForProject(projectData),
                ActivityTargetType.PROJECT,
                projectData.id(),
                0.0 // Project closure is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project reopened events.
     *
     * <p>Records PROJECT_REOPENED activity event with 0 XP. Reopening indicates
     * the project is being reactivated.
     *
     * <p>The actor is the user who reopened the project (from webhook sender),
     * falling back to the project creator for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectReopened(DomainEvent.ProjectReopened event) {
        var projectData = event.project();
        if (!hasValidScopeId("Project reopened", projectData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = projectData.updatedAt() != null ? projectData.updatedAt() : Instant.now();
        // Use actorId (webhook sender) if available, fall back to creatorId for sync
        Long actorId = projectData.actorId() != null ? projectData.actorId() : projectData.creatorId();
        safeRecord("project reopened", projectData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_REOPENED,
                occurredAt,
                getActorOrNull(actorId),
                getRepositoryForProject(projectData),
                ActivityTargetType.PROJECT,
                projectData.id(),
                0.0 // Project reopening is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project deleted events.
     *
     * <p>Records PROJECT_DELETED activity event with 0 XP. Note that we only
     * have the project ID since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectDeleted(DomainEvent.ProjectDeleted event) {
        Long projectId = event.projectId();
        if (!hasValidScopeId("Project deleted", projectId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording project deleted event: projectId={}", projectId);
        safeRecord("project deleted", projectId, () ->
            activityEventService.recordDeleted(
                event.context().scopeId(),
                ActivityEventType.PROJECT_DELETED,
                Instant.now(),
                ActivityTargetType.PROJECT,
                projectId
            )
        );
    }

    // ========================================================================
    // Project Item Events (Work Item Tracking)
    // ========================================================================

    /**
     * Handle project item created events.
     *
     * <p>Records PROJECT_ITEM_CREATED activity event with 0 XP. Adding items to
     * a project is tracked for activity completeness.
     *
     * <p>The actor is the user who created the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemCreated(DomainEvent.ProjectItemCreated event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item created", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            itemData.createdAt() != null
                ? itemData.createdAt()
                : itemData.updatedAt() != null
                    ? itemData.updatedAt()
                    : Instant.now();
        User actor = getActorOrNull(itemData.actorId());
        safeRecord("project item created", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_CREATED,
                occurredAt,
                actor,
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Project item events are tracked without XP
            )
        );
    }

    /**
     * Handle project item updated events.
     *
     * <p>Records PROJECT_ITEM_UPDATED activity event with 0 XP. Updates to item
     * field values or status are tracked for audit purposes.
     *
     * <p>The actor is the user who updated the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemUpdated(DomainEvent.ProjectItemUpdated event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item updated", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = itemData.updatedAt() != null ? itemData.updatedAt() : Instant.now();
        safeRecord("project item updated", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_UPDATED,
                occurredAt,
                getActorOrNull(itemData.actorId()),
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Item updates are workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project item archived events.
     *
     * <p>Records PROJECT_ITEM_ARCHIVED activity event with 0 XP. Archiving
     * hides items from active view.
     *
     * <p>The actor is the user who archived the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemArchived(DomainEvent.ProjectItemArchived event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item archived", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = itemData.updatedAt() != null ? itemData.updatedAt() : Instant.now();
        safeRecord("project item archived", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_ARCHIVED,
                occurredAt,
                getActorOrNull(itemData.actorId()),
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Item archiving is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project item restored events.
     *
     * <p>Records PROJECT_ITEM_RESTORED activity event with 0 XP. Restoring
     * brings archived items back to active view.
     *
     * <p>The actor is the user who restored the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemRestored(DomainEvent.ProjectItemRestored event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item restored", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = itemData.updatedAt() != null ? itemData.updatedAt() : Instant.now();
        safeRecord("project item restored", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_RESTORED,
                occurredAt,
                getActorOrNull(itemData.actorId()),
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Item restoration is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project item deleted events.
     *
     * <p>Records PROJECT_ITEM_DELETED activity event with 0 XP. Note that we
     * only have the item ID since the entity was deleted.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemDeleted(DomainEvent.ProjectItemDeleted event) {
        Long itemId = event.itemId();
        if (!hasValidScopeId("Project item deleted", itemId, event.context().scopeId())) {
            return;
        }
        log.debug("Recording project item deleted event: itemId={}", itemId);
        safeRecord("project item deleted", itemId, () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_DELETED,
                Instant.now(),
                null,
                resolveRepositoryForProjectId(event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemId,
                0.0
            )
        );
    }

    /**
     * Handle project item converted events.
     *
     * <p>Records PROJECT_ITEM_CONVERTED activity event with 0 XP. Conversion
     * occurs when a draft issue is converted to a real issue.
     *
     * <p>The actor is the user who converted the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemConverted(DomainEvent.ProjectItemConverted event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item converted", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = itemData.updatedAt() != null ? itemData.updatedAt() : Instant.now();
        safeRecord("project item converted", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_CONVERTED,
                occurredAt,
                getActorOrNull(itemData.actorId()),
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Item conversion is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Handle project item reordered events.
     *
     * <p>Records PROJECT_ITEM_REORDERED activity event with 0 XP. Reordering
     * occurs when item position changes in the project view.
     *
     * <p>The actor is the user who reordered the item (from webhook sender),
     * which may be null for sync operations.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectItemReordered(DomainEvent.ProjectItemReordered event) {
        var itemData = event.item();
        if (!hasValidScopeId("Project item reordered", itemData.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = itemData.updatedAt() != null ? itemData.updatedAt() : Instant.now();
        safeRecord("project item reordered", itemData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_ITEM_REORDERED,
                occurredAt,
                getActorOrNull(itemData.actorId()),
                resolveRepositoryForProjectItem(itemData, event.projectId()),
                ActivityTargetType.PROJECT_ITEM,
                itemData.id(),
                0.0 // Item reordering is workflow tracking, no XP reward
            )
        );
    }

    /**
     * Get the repository reference for a project, or null if the project is
     * organization-scoped (not associated with a specific repository).
     *
     * @param projectData the project data from the event
     * @return Repository reference if repository-scoped, null otherwise
     */
    @Nullable
    private de.tum.in.www1.hephaestus.gitprovider.repository.Repository getRepositoryForProject(
        EventPayload.ProjectData projectData
    ) {
        if (projectData.ownerType() == Project.OwnerType.REPOSITORY) {
            return repositoryRepository.getReferenceById(projectData.ownerId());
        }
        // Organization or user-scoped projects don't have a direct repository link
        return null;
    }

    @Nullable
    private de.tum.in.www1.hephaestus.gitprovider.repository.Repository resolveRepositoryForProjectId(
        @Nullable Long projectId
    ) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
        if (project.getOwnerType() == Project.OwnerType.REPOSITORY) {
            return repositoryRepository.getReferenceById(project.getOwnerId());
        }
        return null;
    }

    @Nullable
    private de.tum.in.www1.hephaestus.gitprovider.repository.Repository resolveRepositoryForProjectItem(
        EventPayload.ProjectItemData itemData,
        @Nullable Long projectId
    ) {
        if (itemData == null) {
            return resolveRepositoryForProjectId(projectId);
        }
        if (itemData.issueId() != null) {
            var issue = issueRepository.findById(itemData.issueId()).orElse(null);
            if (issue != null && issue.getRepository() != null) {
                return repositoryRepository.getReferenceById(issue.getRepository().getId());
            }
        }
        return resolveRepositoryForProjectId(projectId);
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

    // ========================================================================
    // Commit Events
    // ========================================================================

    /**
     * Handle commit created events.
     *
     * <p>Records COMMIT_CREATED activity event. If the author is unknown (null),
     * the event is still recorded for audit purposes but with 0 XP.
     *
     * <p>XP is only awarded when we can attribute the commit to a known user.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitCreated(DomainEvent.CommitCreated event) {
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
                commitData.id(),
                xpForActor(actor, xpCalc.getXpCommitCreated())
            )
        );
    }

    // ========================================================================
    // Project Status Update Events
    // ========================================================================

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectStatusUpdateCreated(DomainEvent.ProjectStatusUpdateCreated event) {
        var data = event.statusUpdate();
        if (!hasValidScopeId("Project status update created", data.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt =
            data.createdAt() != null ? data.createdAt() : data.updatedAt() != null ? data.updatedAt() : Instant.now();
        safeRecord("project status update created", data.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_STATUS_UPDATE_CREATED,
                occurredAt,
                getActorOrNull(data.creatorId()),
                resolveRepositoryForProjectId(event.projectId()),
                ActivityTargetType.PROJECT_STATUS_UPDATE,
                data.id(),
                0.0 // Status updates tracked without XP
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectStatusUpdateUpdated(DomainEvent.ProjectStatusUpdateUpdated event) {
        var data = event.statusUpdate();
        if (!hasValidScopeId("Project status update updated", data.id(), event.context().scopeId())) {
            return;
        }
        Instant occurredAt = data.updatedAt() != null ? data.updatedAt() : Instant.now();
        safeRecord("project status update updated", data.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_STATUS_UPDATE_UPDATED,
                occurredAt,
                getActorOrNull(data.creatorId()),
                resolveRepositoryForProjectId(event.projectId()),
                ActivityTargetType.PROJECT_STATUS_UPDATE,
                data.id(),
                0.0
            )
        );
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectStatusUpdateDeleted(DomainEvent.ProjectStatusUpdateDeleted event) {
        Long id = event.statusUpdateId();
        if (!hasValidScopeId("Project status update deleted", id, event.context().scopeId())) {
            return;
        }
        safeRecord("project status update deleted", id, () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.PROJECT_STATUS_UPDATE_DELETED,
                Instant.now(),
                null,
                resolveRepositoryForProjectId(event.projectId()),
                ActivityTargetType.PROJECT_STATUS_UPDATE,
                id,
                0.0
            )
        );
    }
}

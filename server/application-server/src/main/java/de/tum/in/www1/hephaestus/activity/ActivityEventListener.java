package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
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
@Component
public class ActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventListener.class);

    private final ActivityEventService activityEventService;
    private final ExperiencePointCalculator xpCalc;
    private final PullRequestReviewRepository reviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;

    public ActivityEventListener(
        ActivityEventService activityEventService,
        ExperiencePointCalculator xpCalc,
        PullRequestReviewRepository reviewRepository,
        IssueCommentRepository issueCommentRepository,
        UserRepository userRepository,
        RepositoryRepository repositoryRepository
    ) {
        this.activityEventService = activityEventService;
        this.xpCalc = xpCalc;
        this.reviewRepository = reviewRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
    }

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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR created", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null || pr.createdAt() == null) {
            log.warn(
                "PR created event missing required data: prId={}, authorId={}, createdAt={}",
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
                xpCalc.getXpPullRequestOpened(),
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestMerged(DomainEvent.PullRequestMerged event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR merged", pr.id(), event.context().scopeId())) {
            return;
        }
        // For merged PRs, prefer mergedBy, fall back to author
        Long awardeeId = pr.mergedById() != null ? pr.mergedById() : pr.authorId();
        if (awardeeId == null) {
            log.warn("PR merged event missing awardee: prId={}", pr.id());
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
                xpCalc.getXpPullRequestMerged(),
                mapSource(event.context().source())
            )
        );
    }

    @Async
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
            log.warn("PR closed event missing authorId: prId={}", pr.id());
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
                0.0,
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(DomainEvent.PullRequestReopened event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR reopened", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.warn("PR reopened event missing authorId: prId={}", pr.id());
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
                0.0, // Reopening is lifecycle tracking, no XP reward
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        var pr = event.pullRequest();
        if (!hasValidScopeId("PR ready", pr.id(), event.context().scopeId())) {
            return;
        }
        if (pr.authorId() == null) {
            log.warn("PR ready event missing authorId: prId={}", pr.id());
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
                xpCalc.getXpPullRequestReady(),
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        var reviewData = event.review();
        if (!hasValidScopeId("Review submitted", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn(
                "Review submitted event missing required data: reviewId={}, authorId={}, repositoryId={}",
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
                xp,
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewDismissed(DomainEvent.ReviewDismissed event) {
        var reviewData = event.review();
        if (!hasValidScopeId("Review dismissed", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn(
                "Review dismissed event missing required data: reviewId={}, authorId={}, repositoryId={}",
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
                xpAdjustment,
                mapSource(event.context().source())
            )
        );
    }

    /**
     * Handle review edited events.
     *
     * <p>When a review is edited (e.g., state changes from COMMENTED to APPROVED),
     * we record a new event with the updated state. The XP is recalculated based
     * on the new review state and all reviews for the PR by this author.
     *
     * <p>Note: This creates a new event rather than updating the original,
     * maintaining an immutable audit trail of all review activity.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewEdited(DomainEvent.ReviewEdited event) {
        var reviewData = event.review();
        if (!hasValidScopeId("Review edited", reviewData.id(), event.context().scopeId())) {
            return;
        }
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn(
                "Review edited event missing required data: reviewId={}, authorId={}, repositoryId={}",
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
        safeRecord("review edited", reviewData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.REVIEW_EDITED,
                occurredAt,
                userRepository.getReferenceById(reviewData.authorId()),
                repositoryRepository.getReferenceById(reviewData.repositoryId()),
                ActivityTargetType.REVIEW,
                reviewData.id(),
                xp,
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(DomainEvent.CommentCreated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Comment created", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.warn(
                "Comment created event missing required data: commentId={}, authorId={}, repositoryId={}",
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
                finalXp,
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentCreated(DomainEvent.ReviewCommentCreated event) {
        var commentData = event.comment();
        if (!hasValidScopeId("Review comment created", commentData.id(), event.context().scopeId())) {
            return;
        }
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.warn(
                "Review comment created event missing required data: commentId={}, authorId={}, repositoryId={}",
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
                commentData.id(),
                xpCalc.getXpReviewComment(),
                mapSource(event.context().source())
            )
        );
    }

    @Async
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
        if (issueData.authorId() == null) {
            log.warn("Issue created event missing authorId: issueId={}", issueData.id());
            return;
        }
        Instant occurredAt = issueData.createdAt() != null ? issueData.createdAt() : Instant.now();
        safeRecord("issue created", issueData.id(), () ->
            activityEventService.record(
                event.context().scopeId(),
                ActivityEventType.ISSUE_CREATED,
                occurredAt,
                userRepository.getReferenceById(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id(),
                xpCalc.getXpIssueCreated(),
                mapSource(event.context().source())
            )
        );
    }

    @Async
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
        if (issueData.authorId() == null) {
            log.warn("Issue closed event missing authorId: issueId={}", issueData.id());
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
                userRepository.getReferenceById(issueData.authorId()),
                repositoryRepository.getReferenceById(issueData.repository().id()),
                ActivityTargetType.ISSUE,
                issueData.id(),
                0.0, // Issue closure is lifecycle tracking, no XP reward
                mapSource(event.context().source())
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

    /**
     * Map event context source to SourceSystem.
     * Defaults to SYSTEM if source is null.
     */
    private SourceSystem mapSource(de.tum.in.www1.hephaestus.gitprovider.common.DataSource source) {
        if (source == null) {
            return SourceSystem.SYSTEM;
        }
        // All data sources from gitprovider are GitHub data
        return SourceSystem.GITHUB;
    }
}

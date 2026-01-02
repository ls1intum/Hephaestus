package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.List;
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
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;

    public ActivityEventListener(
        ActivityEventService activityEventService,
        ExperiencePointCalculator xpCalc,
        PullRequestReviewRepository reviewRepository,
        UserRepository userRepository,
        RepositoryRepository repositoryRepository
    ) {
        this.activityEventService = activityEventService;
        this.xpCalc = xpCalc;
        this.reviewRepository = reviewRepository;
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
            log.error("Failed to record {}: {}", eventName, entityId, e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        var pr = event.pullRequest();
        if (pr.authorId() == null || pr.createdAt() == null) {
            log.warn("PR created event missing required data: prId={}, authorId={}, createdAt={}",
                pr.id(), pr.authorId(), pr.createdAt());
            return;
        }
        safeRecord("PR opened", pr.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
                event.context().workspaceId(),
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
                event.context().workspaceId(),
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
        if (pr.authorId() == null) {
            log.warn("PR reopened event missing authorId: prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR reopened", pr.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
        if (pr.authorId() == null) {
            log.warn("PR ready event missing authorId: prId={}", pr.id());
            return;
        }
        Instant occurredAt = pr.updatedAt() != null ? pr.updatedAt() : Instant.now();
        safeRecord("PR ready", pr.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn("Review submitted event missing required data: reviewId={}, authorId={}, repositoryId={}",
                reviewData.id(), reviewData.authorId(), reviewData.repositoryId());
            return;
        }
        // Calculate XP using ALL reviews for this PR by this author (per-PR harmonic mean)
        // This is a necessary query to get the harmonic mean calculation right
        List<PullRequestReview> allReviewsForPrByAuthor = reviewRepository
            .findByPullRequestIdAndAuthorId(reviewData.pullRequestId(), reviewData.authorId());
        double xp = xpCalc.calculateReviewExperiencePoints(allReviewsForPrByAuthor);
        Instant occurredAt = reviewData.submittedAt() != null ? reviewData.submittedAt() : Instant.now();
        safeRecord("review", reviewData.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
        if (reviewData.authorId() == null || reviewData.repositoryId() == null) {
            log.warn("Review dismissed event missing required data: reviewId={}, authorId={}, repositoryId={}",
                reviewData.id(), reviewData.authorId(), reviewData.repositoryId());
            return;
        }
        // Record a REVIEW_DISMISSED event with 0 XP - dismissals don't affect XP
        // since dismissed reviews still count for the leaderboard
        double xpAdjustment = 0.0;
        
        safeRecord("review dismissed", reviewData.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(DomainEvent.CommentCreated event) {
        var commentData = event.comment();
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.warn("Comment created event missing required data: commentId={}, authorId={}, repositoryId={}",
                commentData.id(), commentData.authorId(), commentData.repositoryId());
            return;
        }
        // Note: XP calculation currently needs body length - we have body in commentData
        // For now, use a fixed XP since we don't have the full comment entity
        // TODO: Consider adding body length to CommentData or simplifying XP calculation
        double xp = xpCalc.getXpReviewComment(); // Use review comment XP as fallback
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        safeRecord("comment", commentData.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
                ActivityEventType.COMMENT_CREATED,
                occurredAt,
                userRepository.getReferenceById(commentData.authorId()),
                repositoryRepository.getReferenceById(commentData.repositoryId()),
                ActivityTargetType.ISSUE_COMMENT,
                commentData.id(),
                xp,
                mapSource(event.context().source())
            )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentCreated(DomainEvent.ReviewCommentCreated event) {
        var commentData = event.comment();
        if (commentData.authorId() == null || commentData.repositoryId() == null) {
            log.warn("Review comment created event missing required data: commentId={}, authorId={}, repositoryId={}",
                commentData.id(), commentData.authorId(), commentData.repositoryId());
            return;
        }
        Instant occurredAt = commentData.createdAt() != null ? commentData.createdAt() : Instant.now();
        safeRecord("review comment", commentData.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
        if (issueData.authorId() == null) {
            log.warn("Issue created event missing authorId: issueId={}", issueData.id());
            return;
        }
        Instant occurredAt = issueData.createdAt() != null ? issueData.createdAt() : Instant.now();
        safeRecord("issue created", issueData.id(), () ->
            activityEventService.record(
                event.context().workspaceId(),
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
                event.context().workspaceId(),
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
    private SourceSystem mapSource(EventContext.Source source) {
        if (source == null) {
            return SourceSystem.SYSTEM;
        }
        if (source == EventContext.Source.WEBHOOK || source == EventContext.Source.GRAPHQL_SYNC) {
            return SourceSystem.GITHUB;
        }
        return SourceSystem.SYSTEM;
    }
}

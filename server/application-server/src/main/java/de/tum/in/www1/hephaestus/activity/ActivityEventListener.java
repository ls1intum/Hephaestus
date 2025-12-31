package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
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
 */
@Component
public class ActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventListener.class);

    private final ActivityEventService activityEventService;
    private final ExperiencePointCalculator xpCalc;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;

    public ActivityEventListener(
        ActivityEventService activityEventService,
        ExperiencePointCalculator xpCalc,
        PullRequestReviewRepository reviewRepository,
        PullRequestRepository pullRequestRepository,
        IssueCommentRepository issueCommentRepository,
        PullRequestReviewCommentRepository reviewCommentRepository
    ) {
        this.activityEventService = activityEventService;
        this.xpCalc = xpCalc;
        this.reviewRepository = reviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.reviewCommentRepository = reviewCommentRepository;
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
        pullRequestRepository
            .findById(event.pullRequest().id())
            .ifPresent(pr ->
                safeRecord("PR opened", pr.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        ActivityEventType.PULL_REQUEST_OPENED,
                        pr.getCreatedAt(),
                        pr.getAuthor(),
                        pr.getRepository(),
                        ActivityTargetType.PULL_REQUEST,
                        pr.getId(),
                        xpCalc.getXpPullRequestOpened(),
                        mapSource(event.context().source())
                    )
                )
            );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestMerged(DomainEvent.PullRequestMerged event) {
        pullRequestRepository
            .findById(event.pullRequest().id())
            .ifPresent(pr -> {
                var awardee = pr.getMergedBy() != null ? pr.getMergedBy() : pr.getAuthor();
                safeRecord("PR merged", pr.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        ActivityEventType.PULL_REQUEST_MERGED,
                        pr.getMergedAt() != null ? pr.getMergedAt() : pr.getUpdatedAt(),
                        awardee,
                        pr.getRepository(),
                        ActivityTargetType.PULL_REQUEST,
                        pr.getId(),
                        xpCalc.getXpPullRequestMerged(),
                        mapSource(event.context().source())
                    )
                );
            });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(DomainEvent.PullRequestClosed event) {
        if (event.wasMerged()) {
            return;
        }
        pullRequestRepository
            .findById(event.pullRequest().id())
            .ifPresent(pr ->
                safeRecord("PR closed", pr.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        ActivityEventType.PULL_REQUEST_CLOSED,
                        pr.getClosedAt() != null ? pr.getClosedAt() : pr.getUpdatedAt(),
                        pr.getAuthor(),
                        pr.getRepository(),
                        ActivityTargetType.PULL_REQUEST,
                        pr.getId(),
                        0.0,
                        mapSource(event.context().source())
                    )
                )
            );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        reviewRepository
            .findById(event.review().id())
            .ifPresent(review -> {
                PullRequest pr = review.getPullRequest();
                if (pr == null) {
                    log.warn("Review has no PR: {}", review.getId());
                    return;
                }
                double xp = xpCalc.calculateReviewExperiencePoints(List.of(review));
                safeRecord("review", review.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        mapReviewState(review.getState()),
                        review.getSubmittedAt() != null ? review.getSubmittedAt() : Instant.now(),
                        review.getAuthor(),
                        pr.getRepository(),
                        ActivityTargetType.REVIEW,
                        review.getId(),
                        xp,
                        mapSource(event.context().source())
                    )
                );
            });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(DomainEvent.CommentCreated event) {
        issueCommentRepository
            .findById(event.comment().id())
            .ifPresent(comment -> {
                var issue = comment.getIssue();
                if (issue == null) {
                    log.warn("Comment has no issue: {}", comment.getId());
                    return;
                }
                double xp = xpCalc.calculateIssueCommentExperiencePoints(comment);
                safeRecord("comment", comment.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        ActivityEventType.COMMENT_CREATED,
                        comment.getCreatedAt(),
                        comment.getAuthor(),
                        issue.getRepository(),
                        ActivityTargetType.ISSUE_COMMENT,
                        comment.getId(),
                        xp,
                        mapSource(event.context().source())
                    )
                );
            });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCommentCreated(DomainEvent.ReviewCommentCreated event) {
        reviewCommentRepository
            .findById(event.comment().id())
            .ifPresent(comment -> {
                var review = comment.getReview();
                PullRequest pr = review != null ? review.getPullRequest() : null;
                if (pr == null) {
                    log.warn("Review comment has no PR: {}", comment.getId());
                    return;
                }
                safeRecord("review comment", comment.getId(), () ->
                    activityEventService.record(
                        event.context().workspaceId(),
                        ActivityEventType.REVIEW_COMMENT_CREATED,
                        comment.getCreatedAt(),
                        comment.getAuthor(),
                        pr.getRepository(),
                        ActivityTargetType.REVIEW_COMMENT,
                        comment.getId(),
                        xpCalc.getXpReviewComment(),
                        mapSource(event.context().source())
                    )
                );
            });
    }

    private ActivityEventType mapReviewState(PullRequestReview.State state) {
        if (state == PullRequestReview.State.APPROVED) {
            return ActivityEventType.REVIEW_APPROVED;
        }
        if (state == PullRequestReview.State.CHANGES_REQUESTED) {
            return ActivityEventType.REVIEW_CHANGES_REQUESTED;
        }
        return ActivityEventType.REVIEW_COMMENTED;
    }

    private SourceSystem mapSource(EventContext.Source source) {
        if (source == EventContext.Source.WEBHOOK || source == EventContext.Source.GRAPHQL_SYNC) {
            return SourceSystem.GITHUB;
        }
        return SourceSystem.SYSTEM;
    }
}

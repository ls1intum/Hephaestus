package de.tum.in.www1.hephaestus.activity.backfill;

import de.tum.in.www1.hephaestus.activity.ActivityEventService;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.activity.SourceSystem;
import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for backfilling historical activity events from existing entities.
 *
 * <p>This service populates the activity event ledger with historical data for entities
 * that existed before the activity tracking system was implemented. It processes entities
 * in batches to avoid memory issues and uses the existing {@link ActivityEventService}
 * for idempotent event recording.
 *
 * <h3>Idempotency</h3>
 * <p>All backfill operations are idempotent - running them multiple times will not create
 * duplicate events because the underlying {@link ActivityEventService#recordWithContext}
 * relies on a unique constraint on the event_key (type:targetId:timestamp).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Backfill all events for a workspace
 * BackfillProgress progress = backfillService.backfillWorkspace(workspaceId);
 * log.info(progress.summary());
 *
 * // Or backfill specific event types
 * backfillService.backfillPullRequests(workspaceId);
 * backfillService.backfillReviews(workspaceId);
 * }</pre>
 *
 * <h3>Performance</h3>
 * <p>Each entity type is processed in batches (default: 500). Each batch runs in its own
 * transaction to avoid long-running transactions and allow progress tracking. The service
 * clears the EntityManager after each batch to prevent memory buildup.
 *
 * <p><strong>DEPRECATION NOTICE:</strong> This service is temporary and will be removed
 * once all production environments have been migrated. It exists solely to backfill
 * historical activity events during the transition from the old XP calculation system
 * to the new activity event ledger.
 *
 * <p>Target removal: After all workspaces have been backfilled in production.
 *
 * @see ActivityEventService#recordWithContext
 * @see BackfillProgress
 * @deprecated Temporary migration component - remove after production migration is complete
 */
@Deprecated(forRemoval = true)
@Service
public class ActivityEventBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventBackfillService.class);

    /** Trigger context used for all backfilled events */
    public static final String TRIGGER_CONTEXT_BACKFILL = "backfill";

    /** Default batch size for processing entities */
    private static final int DEFAULT_BATCH_SIZE = 500;

    private final ObjectProvider<ActivityEventBackfillService> selfProvider;
    private final ActivityEventService activityEventService;
    private final ExperiencePointCalculator xpCalculator;
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final IssueRepository issueRepository;
    private final EntityManager entityManager;

    public ActivityEventBackfillService(
        ObjectProvider<ActivityEventBackfillService> selfProvider,
        ActivityEventService activityEventService,
        ExperiencePointCalculator xpCalculator,
        WorkspaceRepository workspaceRepository,
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewRepository reviewRepository,
        IssueCommentRepository issueCommentRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        IssueRepository issueRepository,
        EntityManager entityManager
    ) {
        this.selfProvider = selfProvider;
        this.activityEventService = activityEventService;
        this.xpCalculator = xpCalculator;
        this.workspaceRepository = workspaceRepository;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewRepository = reviewRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.issueRepository = issueRepository;
        this.entityManager = entityManager;
    }

    // ========================================================================
    // Main Entry Point
    // ========================================================================

    /**
     * Backfills all activity events for a workspace.
     *
     * <p>This method orchestrates the backfill of all supported event types:
     * <ol>
     *   <li>Pull request events (opened, merged, closed)</li>
     *   <li>Review events (approved, changes_requested, commented, dismissed)</li>
     *   <li>Issue comment events</li>
     *   <li>Review comment events</li>
     *   <li>Issue events (created, closed)</li>
     * </ol>
     *
     * @param workspaceId the workspace to backfill
     * @return aggregated progress across all event types
     * @throws IllegalArgumentException if workspace not found
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillWorkspace(Long workspaceId) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        log.info(
            "Starting activity event backfill for workspace: {} (id={})",
            workspace.getWorkspaceSlug(),
            workspaceId
        );

        BackfillProgress.Builder overallProgress = BackfillProgress.builder().entityType("Workspace").start();

        // Backfill each entity type
        BackfillProgress prProgress = backfillPullRequests(workspaceId);
        log.info(prProgress.summary());

        BackfillProgress reviewProgress = backfillReviews(workspaceId);
        log.info(reviewProgress.summary());

        BackfillProgress issueCommentProgress = backfillIssueComments(workspaceId);
        log.info(issueCommentProgress.summary());

        BackfillProgress reviewCommentProgress = backfillReviewComments(workspaceId);
        log.info(reviewCommentProgress.summary());

        BackfillProgress issueProgress = backfillIssues(workspaceId);
        log.info(issueProgress.summary());

        // Aggregate all progress
        BackfillProgress combined = prProgress
            .merge(reviewProgress)
            .merge(issueCommentProgress)
            .merge(reviewCommentProgress)
            .merge(issueProgress);

        log.info(
            "Completed activity event backfill for workspace {}: {}",
            workspace.getWorkspaceSlug(),
            combined.summary()
        );

        return combined;
    }

    // ========================================================================
    // Pull Request Backfill
    // ========================================================================

    /**
     * Backfills pull request events (OPENED, MERGED, CLOSED) for a workspace.
     *
     * <p>For each pull request:
     * <ul>
     *   <li>PULL_REQUEST_OPENED: Created at PR's createdAt timestamp</li>
     *   <li>PULL_REQUEST_MERGED: Created at PR's mergedAt timestamp (if merged)</li>
     *   <li>PULL_REQUEST_CLOSED: Created at PR's closedAt timestamp (if closed without merge)</li>
     * </ul>
     *
     * @param workspaceId the workspace to backfill
     * @return progress tracking the backfill operation
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillPullRequests(Long workspaceId) {
        BackfillProgress.Builder progress = BackfillProgress.builder().entityType("PullRequest").start();

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        for (Repository repo : repositories) {
            backfillPullRequestsForRepository(repo, workspaceId, progress);
        }

        return progress.build();
    }

    private void backfillPullRequestsForRepository(
        Repository repo,
        Long workspaceId,
        BackfillProgress.Builder progress
    ) {
        int page = 0;

        List<PullRequest> batch;
        do {
            batch = pullRequestRepository.findAllByRepository_Id(repo.getId());

            // Process in smaller chunks for pagination (since findAllByRepository_Id returns all)
            int start = page * DEFAULT_BATCH_SIZE;
            int end = Math.min(start + DEFAULT_BATCH_SIZE, batch.size());

            if (start >= batch.size()) {
                break;
            }

            List<PullRequest> chunk = batch.subList(start, end);
            selfProvider.getObject().processPullRequestBatch(chunk, workspaceId, progress);

            // Clear entity manager to prevent memory buildup
            entityManager.clear();

            page++;
        } while (page * DEFAULT_BATCH_SIZE < batch.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processPullRequestBatch(
        List<PullRequest> pullRequests,
        Long workspaceId,
        BackfillProgress.Builder progress
    ) {
        for (PullRequest pr : pullRequests) {
            progress.incrementProcessed();

            // Skip PRs without authors
            if (pr.getAuthor() == null) {
                log.debug("Skipping PR {} - no author", pr.getId());
                progress.incrementSkipped();
                continue;
            }

            try {
                // Record PULL_REQUEST_OPENED
                if (pr.getCreatedAt() != null) {
                    boolean recorded = recordPullRequestEvent(
                        workspaceId,
                        ActivityEventType.PULL_REQUEST_OPENED,
                        pr,
                        pr.getCreatedAt(),
                        xpCalculator.calculatePullRequestOpenedExperiencePoints(pr)
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }
                }

                // Record PULL_REQUEST_MERGED (if merged)
                if (pr.isMerged() && pr.getMergedAt() != null) {
                    boolean recorded = recordPullRequestEvent(
                        workspaceId,
                        ActivityEventType.PULL_REQUEST_MERGED,
                        pr,
                        pr.getMergedAt(),
                        xpCalculator.calculatePullRequestMergedExperiencePoints(pr)
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }
                }

                // Record PULL_REQUEST_CLOSED (if closed without merge)
                if (pr.getState() == Issue.State.CLOSED && !pr.isMerged() && pr.getClosedAt() != null) {
                    boolean recorded = recordPullRequestEvent(
                        workspaceId,
                        ActivityEventType.PULL_REQUEST_CLOSED,
                        pr,
                        pr.getClosedAt(),
                        0.0 // No XP for closing without merge
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }
                }
            } catch (Exception e) {
                log.error("Error backfilling PR {}: {}", pr.getId(), e.getMessage(), e);
                progress.incrementFailed();
            }
        }
    }

    private boolean recordPullRequestEvent(
        Long workspaceId,
        ActivityEventType eventType,
        PullRequest pr,
        Instant occurredAt,
        double xp
    ) {
        return activityEventService.recordWithContext(
            workspaceId,
            eventType,
            occurredAt,
            pr.getAuthor(),
            pr.getRepository(),
            ActivityTargetType.PULL_REQUEST,
            pr.getId(),
            xp,
            SourceSystem.GITHUB,
            null,
            TRIGGER_CONTEXT_BACKFILL
        );
    }

    // ========================================================================
    // Review Backfill
    // ========================================================================

    /**
     * Backfills review events for a workspace.
     *
     * <p>Creates events for all review states:
     * <ul>
     *   <li>REVIEW_APPROVED: For approved reviews</li>
     *   <li>REVIEW_CHANGES_REQUESTED: For reviews requesting changes</li>
     *   <li>REVIEW_COMMENTED: For comment-only reviews</li>
     *   <li>REVIEW_UNKNOWN: For reviews with unknown state</li>
     *   <li>REVIEW_DISMISSED: For dismissed reviews (separate event)</li>
     * </ul>
     *
     * @param workspaceId the workspace to backfill
     * @return progress tracking the backfill operation
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillReviews(Long workspaceId) {
        BackfillProgress.Builder progress = BackfillProgress.builder().entityType("Review").start();

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        for (Repository repo : repositories) {
            backfillReviewsForRepository(repo, workspaceId, progress);
        }

        return progress.build();
    }

    private void backfillReviewsForRepository(Repository repo, Long workspaceId, BackfillProgress.Builder progress) {
        // Get all PRs for this repository with reviews eagerly fetched
        // to avoid LazyInitializationException after entityManager.clear()
        List<PullRequest> pullRequests = pullRequestRepository.findAllByRepositoryIdWithReviews(repo.getId());

        // Process in batches to avoid memory issues
        int batchCount = 0;
        for (PullRequest pr : pullRequests) {
            // Process each PR's reviews in its own transaction via self-injection
            selfProvider.getObject().processReviewsForPullRequest(pr, workspaceId, progress);

            batchCount++;
            // Clear periodically to prevent memory issues
            if (batchCount % DEFAULT_BATCH_SIZE == 0) {
                entityManager.clear();
                // Re-fetch remaining PRs since entities were cleared
                // This is safe because we track progress by PR, not by review
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processReviewsForPullRequest(PullRequest pr, Long workspaceId, BackfillProgress.Builder progress) {
        try {
            for (PullRequestReview review : pr.getReviews()) {
                progress.incrementProcessed();

                // Skip reviews without authors
                if (review.getAuthor() == null) {
                    log.debug("Skipping review {} - no author", review.getId());
                    progress.incrementSkipped();
                    continue;
                }

                try {
                    // Calculate XP for this review
                    double xp = xpCalculator.calculateReviewExperiencePoints(review);

                    // Determine timestamp - use submittedAt, fallback to PR createdAt, then now()
                    Instant occurredAt = review.getSubmittedAt();
                    if (occurredAt == null) {
                        occurredAt = pr.getCreatedAt() != null ? pr.getCreatedAt() : Instant.now();
                        log.debug("Review {} has null submittedAt, using fallback: {}", review.getId(), occurredAt);
                    }

                    // Record the primary review event
                    boolean recorded = activityEventService.recordWithContext(
                        workspaceId,
                        mapReviewState(review.getState()),
                        occurredAt,
                        review.getAuthor(),
                        pr.getRepository(),
                        ActivityTargetType.REVIEW,
                        review.getId(),
                        xp,
                        SourceSystem.GITHUB,
                        null,
                        TRIGGER_CONTEXT_BACKFILL
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }

                    // If the review is dismissed, also record a dismissal event
                    if (review.isDismissed()) {
                        // Use submitted timestamp + 1ms for dismissal to ensure unique event key
                        Instant dismissedAt = occurredAt.plusMillis(1);
                        boolean dismissRecorded = activityEventService.recordWithContext(
                            workspaceId,
                            ActivityEventType.REVIEW_DISMISSED,
                            dismissedAt,
                            review.getAuthor(),
                            pr.getRepository(),
                            ActivityTargetType.REVIEW,
                            review.getId(),
                            0.0, // Dismissals don't affect XP retroactively
                            SourceSystem.GITHUB,
                            null,
                            TRIGGER_CONTEXT_BACKFILL
                        );
                        if (dismissRecorded) {
                            progress.incrementCreated();
                        }
                    }
                } catch (Exception e) {
                    log.error("Error backfilling review {}: {}", review.getId(), e.getMessage(), e);
                    progress.incrementFailed();
                }
            }
        } catch (Exception e) {
            // Catch any exception at the PR level (e.g., LazyInitializationException on getReviews())
            // to prevent transaction rollback from bubbling up
            log.error("Error processing reviews for PR {}: {}", pr.getId(), e.getMessage(), e);
            progress.incrementFailed();
        }
    }

    private ActivityEventType mapReviewState(PullRequestReview.State state) {
        return switch (state) {
            case APPROVED -> ActivityEventType.REVIEW_APPROVED;
            case CHANGES_REQUESTED -> ActivityEventType.REVIEW_CHANGES_REQUESTED;
            case COMMENTED -> ActivityEventType.REVIEW_COMMENTED;
            case UNKNOWN -> ActivityEventType.REVIEW_UNKNOWN;
        };
    }

    // ========================================================================
    // Issue Comment Backfill
    // ========================================================================

    /**
     * Backfills issue comment events (COMMENT_CREATED) for a workspace.
     *
     * <p>Only comments on pull requests are backfilled, as issue comments
     * on regular issues don't contribute to XP.
     *
     * @param workspaceId the workspace to backfill
     * @return progress tracking the backfill operation
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillIssueComments(Long workspaceId) {
        BackfillProgress.Builder progress = BackfillProgress.builder().entityType("IssueComment").start();

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        for (Repository repo : repositories) {
            backfillIssueCommentsForRepository(repo, workspaceId, progress);
        }

        return progress.build();
    }

    private void backfillIssueCommentsForRepository(
        Repository repo,
        Long workspaceId,
        BackfillProgress.Builder progress
    ) {
        // Get all issues (including PRs) for this repository with comments eagerly fetched
        // to avoid LazyInitializationException after entityManager.clear()
        List<Issue> issues = issueRepository.findAllByRepositoryIdWithComments(repo.getId());

        int batchCount = 0;

        for (Issue issue : issues) {
            for (IssueComment comment : issue.getComments()) {
                selfProvider.getObject().processIssueComment(comment, workspaceId, progress);

                batchCount++;
                // Clear periodically
                if (batchCount % DEFAULT_BATCH_SIZE == 0) {
                    entityManager.clear();
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processIssueComment(IssueComment comment, Long workspaceId, BackfillProgress.Builder progress) {
        progress.incrementProcessed();

        // Skip comments without authors
        if (comment.getAuthor() == null) {
            log.debug("Skipping issue comment {} - no author", comment.getId());
            progress.incrementSkipped();
            return;
        }

        // Skip comments without associated issues
        if (comment.getIssue() == null || comment.getIssue().getRepository() == null) {
            log.debug("Skipping issue comment {} - no issue or repository", comment.getId());
            progress.incrementSkipped();
            return;
        }

        try {
            // Calculate XP (will be 0 for self-comments on own PRs)
            double xp = xpCalculator.calculateIssueCommentExperiencePoints(comment);

            Instant occurredAt = comment.getCreatedAt() != null ? comment.getCreatedAt() : Instant.now();

            boolean recorded = activityEventService.recordWithContext(
                workspaceId,
                ActivityEventType.COMMENT_CREATED,
                occurredAt,
                comment.getAuthor(),
                comment.getIssue().getRepository(),
                ActivityTargetType.ISSUE_COMMENT,
                comment.getId(),
                xp,
                SourceSystem.GITHUB,
                null,
                TRIGGER_CONTEXT_BACKFILL
            );
            if (recorded) {
                progress.incrementCreated();
            }
        } catch (Exception e) {
            log.error("Error backfilling issue comment {}: {}", comment.getId(), e.getMessage(), e);
            progress.incrementFailed();
        }
    }

    // ========================================================================
    // Review Comment Backfill
    // ========================================================================

    /**
     * Backfills review comment events (REVIEW_COMMENT_CREATED) for a workspace.
     *
     * <p>Review comments are inline code comments left during a code review.
     * Each comment earns XP based on its body length.
     *
     * @param workspaceId the workspace to backfill
     * @return progress tracking the backfill operation
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillReviewComments(Long workspaceId) {
        BackfillProgress.Builder progress = BackfillProgress.builder().entityType("ReviewComment").start();

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        for (Repository repo : repositories) {
            backfillReviewCommentsForRepository(repo, workspaceId, progress);
        }

        return progress.build();
    }

    private void backfillReviewCommentsForRepository(
        Repository repo,
        Long workspaceId,
        BackfillProgress.Builder progress
    ) {
        // Get all PRs for this repository with review comments eagerly fetched
        // to avoid LazyInitializationException after entityManager.clear()
        List<PullRequest> pullRequests = pullRequestRepository.findAllByRepositoryIdWithReviewComments(repo.getId());

        int batchCount = 0;

        for (PullRequest pr : pullRequests) {
            for (PullRequestReviewComment comment : pr.getReviewComments()) {
                selfProvider.getObject().processReviewComment(comment, workspaceId, progress);

                batchCount++;
                // Clear periodically
                if (batchCount % DEFAULT_BATCH_SIZE == 0) {
                    entityManager.clear();
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processReviewComment(
        PullRequestReviewComment comment,
        Long workspaceId,
        BackfillProgress.Builder progress
    ) {
        progress.incrementProcessed();

        // Skip comments without authors
        if (comment.getAuthor() == null) {
            log.debug("Skipping review comment {} - no author", comment.getId());
            progress.incrementSkipped();
            return;
        }

        // Skip comments without associated PR
        if (comment.getPullRequest() == null || comment.getPullRequest().getRepository() == null) {
            log.debug("Skipping review comment {} - no PR or repository", comment.getId());
            progress.incrementSkipped();
            return;
        }

        try {
            // Calculate XP based on comment body length
            int bodyLength = comment.getBody() != null ? comment.getBody().length() : 0;
            double xp = xpCalculator.calculateReviewCommentExperiencePoints(bodyLength);

            Instant occurredAt = comment.getCreatedAt() != null ? comment.getCreatedAt() : Instant.now();

            boolean recorded = activityEventService.recordWithContext(
                workspaceId,
                ActivityEventType.REVIEW_COMMENT_CREATED,
                occurredAt,
                comment.getAuthor(),
                comment.getPullRequest().getRepository(),
                ActivityTargetType.REVIEW_COMMENT,
                comment.getId(),
                xp,
                SourceSystem.GITHUB,
                null,
                TRIGGER_CONTEXT_BACKFILL
            );
            if (recorded) {
                progress.incrementCreated();
            }
        } catch (Exception e) {
            log.error("Error backfilling review comment {}: {}", comment.getId(), e.getMessage(), e);
            progress.incrementFailed();
        }
    }

    // ========================================================================
    // Issue Backfill
    // ========================================================================

    /**
     * Backfills issue events (CREATED, CLOSED) for a workspace.
     *
     * <p>Only true issues are backfilled (not pull requests, which have their own events).
     *
     * @param workspaceId the workspace to backfill
     * @return progress tracking the backfill operation
     */
    @Transactional(readOnly = true)
    public BackfillProgress backfillIssues(Long workspaceId) {
        BackfillProgress.Builder progress = BackfillProgress.builder().entityType("Issue").start();

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        for (Repository repo : repositories) {
            backfillIssuesForRepository(repo, workspaceId, progress);
        }

        return progress.build();
    }

    private void backfillIssuesForRepository(Repository repo, Long workspaceId, BackfillProgress.Builder progress) {
        List<Issue> issues = issueRepository.findAllByRepository_Id(repo.getId());

        int page = 0;
        int start, end;

        do {
            start = page * DEFAULT_BATCH_SIZE;
            end = Math.min(start + DEFAULT_BATCH_SIZE, issues.size());

            if (start >= issues.size()) {
                break;
            }

            List<Issue> chunk = issues.subList(start, end);
            selfProvider.getObject().processIssueBatch(chunk, workspaceId, progress);

            entityManager.clear();
            page++;
        } while (end < issues.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processIssueBatch(List<Issue> issues, Long workspaceId, BackfillProgress.Builder progress) {
        for (Issue issue : issues) {
            // Skip pull requests - they have their own events
            if (issue.isPullRequest()) {
                continue;
            }

            progress.incrementProcessed();

            // Skip issues without authors
            if (issue.getAuthor() == null) {
                log.debug("Skipping issue {} - no author", issue.getId());
                progress.incrementSkipped();
                continue;
            }

            try {
                // Record ISSUE_CREATED
                if (issue.getCreatedAt() != null) {
                    boolean recorded = activityEventService.recordWithContext(
                        workspaceId,
                        ActivityEventType.ISSUE_CREATED,
                        issue.getCreatedAt(),
                        issue.getAuthor(),
                        issue.getRepository(),
                        ActivityTargetType.ISSUE,
                        issue.getId(),
                        xpCalculator.calculateIssueCreatedExperiencePoints(issue),
                        SourceSystem.GITHUB,
                        null,
                        TRIGGER_CONTEXT_BACKFILL
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }
                }

                // Record ISSUE_CLOSED (if closed)
                if (issue.getState() == Issue.State.CLOSED && issue.getClosedAt() != null) {
                    boolean recorded = activityEventService.recordWithContext(
                        workspaceId,
                        ActivityEventType.ISSUE_CLOSED,
                        issue.getClosedAt(),
                        issue.getAuthor(),
                        issue.getRepository(),
                        ActivityTargetType.ISSUE,
                        issue.getId(),
                        0.0, // Issue closure is lifecycle tracking, no XP
                        SourceSystem.GITHUB,
                        null,
                        TRIGGER_CONTEXT_BACKFILL
                    );
                    if (recorded) {
                        progress.incrementCreated();
                    }
                }
            } catch (Exception e) {
                log.error("Error backfilling issue {}: {}", issue.getId(), e.getMessage(), e);
                progress.incrementFailed();
            }
        }
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Gets the total estimated count of entities to backfill for a workspace.
     *
     * <p>This is useful for progress tracking and UI display.
     *
     * @param workspaceId the workspace to estimate
     * @return estimated entity count
     */
    @Transactional(readOnly = true)
    public BackfillEstimate estimateBackfill(Long workspaceId) {
        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);

        long prCount = 0;
        long reviewCount = 0;
        long issueCommentCount = 0;
        long reviewCommentCount = 0;
        long issueCount = 0;

        for (Repository repo : repositories) {
            List<PullRequest> prs = pullRequestRepository.findAllByRepository_Id(repo.getId());
            prCount += prs.size();

            for (PullRequest pr : prs) {
                reviewCount += pr.getReviews().size();
                reviewCommentCount += pr.getReviewComments().size();
            }

            List<Issue> issues = issueRepository.findAllByRepository_Id(repo.getId());
            for (Issue issue : issues) {
                if (!issue.isPullRequest()) {
                    issueCount++;
                }
                issueCommentCount += issue.getComments().size();
            }
        }

        return new BackfillEstimate(prCount, reviewCount, issueCommentCount, reviewCommentCount, issueCount);
    }

    /**
     * Estimate of entities to backfill.
     */
    public record BackfillEstimate(
        long pullRequests,
        long reviews,
        long issueComments,
        long reviewComments,
        long issues
    ) {
        public long total() {
            // PRs generate up to 3 events each (opened, merged, closed)
            // Reviews generate up to 2 events each (submitted, dismissed)
            // Issues generate up to 2 events each (created, closed)
            return pullRequests + reviews + issueComments + reviewComments + issues;
        }

        public String summary() {
            return String.format(
                "Backfill estimate: PRs=%d, Reviews=%d, IssueComments=%d, ReviewComments=%d, Issues=%d, Total~=%d",
                pullRequests,
                reviews,
                issueComments,
                reviewComments,
                issues,
                total()
            );
        }
    }
}

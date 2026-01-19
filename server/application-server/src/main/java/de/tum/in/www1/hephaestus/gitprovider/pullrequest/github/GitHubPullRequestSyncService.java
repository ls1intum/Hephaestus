package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.PR_SYNC_PAGE_SIZE;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.EmbeddedReviewThreadsDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.EmbeddedReviewsDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubReviewThreadDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.PullRequestWithReviewThreads;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.monitoring.MonitoringProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service for synchronizing GitHub pull requests via GraphQL API.
 * <p>
 * This service fetches PRs using typed GraphQL models and delegates persistence
 * to GitHubPullRequestProcessor. Reviews and review threads (with comments) are
 * fetched inline with PRs to avoid N+1 query patterns - only PRs with more than
 * 10 reviews or 10 review threads require additional API calls for pagination.
 * <p>
 * Supports checkpoint/cursor persistence for resumable sync operations.
 * When a sync target ID is provided, the pagination cursor is persisted after
 * each page, allowing sync to resume from where it left off if interrupted.
 * <p>
 * Supports incremental sync via client-side filtering. Since GitHub's GraphQL API
 * doesn't support filterBy for pull requests, we implement incremental sync by:
 * 1. Fetching PRs ordered by updatedAt DESC
 * 2. Stopping pagination when we encounter a PR with updatedAt older than the last sync timestamp
 */
@Service
public class GitHubPullRequestSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryPullRequests";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestProcessor pullRequestProcessor;
    private final GitHubPullRequestReviewProcessor reviewProcessor;
    private final GitHubPullRequestReviewSyncService reviewSyncService;
    private final GitHubPullRequestReviewCommentSyncService reviewCommentSyncService;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final MonitoringProperties monitoringProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Container for PRs that need additional review pagination.
     */
    private record PullRequestWithReviewCursor(PullRequest pullRequest, String reviewCursor) {}

    /**
     * Container for PRs that need additional review thread/comment pagination.
     */
    private record PullRequestWithThreadCursor(PullRequest pullRequest, String threadCursor) {}

    public GitHubPullRequestSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestProcessor pullRequestProcessor,
        GitHubPullRequestReviewProcessor reviewProcessor,
        GitHubPullRequestReviewSyncService reviewSyncService,
        GitHubPullRequestReviewCommentSyncService reviewCommentSyncService,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        MonitoringProperties monitoringProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.pullRequestProcessor = pullRequestProcessor;
        this.reviewProcessor = reviewProcessor;
        this.reviewSyncService = reviewSyncService;
        this.reviewCommentSyncService = reviewCommentSyncService;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.monitoringProperties = monitoringProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all pull requests for a repository without cursor persistence.
     * <p>
     * Reviews are fetched inline with PRs (first 10 per PR) to eliminate N+1 queries.
     * Only PRs with more than 10 reviews require additional API calls for pagination.
     * <p>
     * This method is kept for backward compatibility. For resumable sync with
     * cursor persistence, use {@link #syncForRepository(Long, Long, Long, String)}.
     * <p>
     * Note: This method intentionally does NOT use @Transactional to avoid long-running
     * transactions. Each page of PRs is processed in its own transaction.
     *
     * @param scopeId      the scope ID for authentication
     * @param repositoryId the repository ID to sync pull requests for
     * @return number of pull requests synced
     */
    public int syncForRepository(Long scopeId, Long repositoryId) {
        return syncForRepository(scopeId, repositoryId, null, null, null);
    }

    /**
     * Synchronizes all pull requests for a repository with cursor persistence support.
     * <p>
     * Reviews are fetched inline with PRs (first 10 per PR) to eliminate N+1 queries.
     * Only PRs with more than 10 reviews require additional API calls for pagination.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId       the scope ID for authentication
     * @param repositoryId  the repository ID to sync pull requests for
     * @param syncTargetId  the sync target ID for cursor persistence (null to disable)
     * @param initialCursor the cursor to resume from (null to start from beginning)
     * @return number of pull requests synced
     */
    public int syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor
    ) {
        return syncForRepository(scopeId, repositoryId, syncTargetId, initialCursor, null);
    }

    /**
     * Synchronizes pull requests for a repository with incremental sync support.
     * <p>
     * When {@code lastSyncTimestamp} is provided and incremental sync is enabled,
     * pagination stops when we encounter a PR with updatedAt older than the timestamp.
     * Since GitHub's GraphQL API doesn't support filterBy for PRs, we implement
     * client-side filtering by stopping pagination early.
     * <p>
     * Reviews are fetched inline with PRs (first 10 per PR) to eliminate N+1 queries.
     * Only PRs with more than 10 reviews require additional API calls for pagination.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     * <p>
     * Note: This method intentionally does NOT use @Transactional to avoid long-running
     * transactions. Each page of PRs is processed in its own transaction to keep
     * individual transactions short (seconds, not minutes) while maintaining data
     * consistency within each page.
     *
     * @param scopeId           the scope ID for authentication
     * @param repositoryId      the repository ID to sync pull requests for
     * @param syncTargetId      the sync target ID for cursor persistence (null to disable)
     * @param initialCursor     the cursor to resume from (null to start from beginning)
     * @param lastSyncTimestamp the timestamp of the last sync for incremental sync (null for full sync)
     * @return number of pull requests synced
     */
    public int syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor,
        @Nullable Instant lastSyncTimestamp
    ) {
        // Fetch repository outside of transaction to avoid holding locks during API calls
        Repository repository = transactionTemplate.execute(status ->
            repositoryRepository.findById(repositoryId).orElse(null)
        );
        if (repository == null) {
            log.debug("Skipped pull request sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped pull request sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.getGraphqlTimeout();

        // Determine if incremental sync is enabled and compute the effective timestamp
        // For first sync (lastSyncTimestamp == null), use configured timeframe as fallback
        Instant effectiveSyncTimestamp = lastSyncTimestamp;
        boolean isIncrementalSync = false;
        if (syncProperties.isIncrementalSyncEnabled()) {
            if (lastSyncTimestamp != null) {
                effectiveSyncTimestamp = lastSyncTimestamp;
                log.info("Starting incremental PR sync: repoName={}, since={}", safeNameWithOwner, lastSyncTimestamp);
            } else {
                // First sync - use configured timeframe as fallback to limit initial data fetch
                effectiveSyncTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(monitoringProperties.getTimeframe())
                    .toInstant();
                log.info(
                    "Starting first PR sync with timeframe fallback: repoName={}, timeframeDays={}, since={}",
                    safeNameWithOwner,
                    monitoringProperties.getTimeframe(),
                    effectiveSyncTimestamp
                );
            }
            isIncrementalSync = true;
        }

        int totalPRsSynced = 0;
        int totalReviewsSynced = 0;
        int totalReviewCommentsSynced = 0;
        List<PullRequestWithReviewCursor> prsNeedingReviewPagination = new ArrayList<>();
        List<PullRequestWithThreadCursor> prsNeedingThreadPagination = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        boolean resuming = initialCursor != null;
        boolean stoppedByIncrementalSync = false;
        final boolean incrementalSync = isIncrementalSync;

        if (resuming) {
            log.info(
                "Resuming pull request sync from checkpoint: repoName={}, cursor={}",
                safeNameWithOwner,
                initialCursor.substring(0, Math.min(20, initialCursor.length())) + "..."
            );
        }

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for pull requests: repoName={}, limit={}",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                // Fetch data from GitHub API outside of transaction
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("first", PR_SYNC_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(timeout);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for pull requests: repoName={}, errors={}",
                        safeNameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical()) {
                    log.warn(
                        "Aborting pull request sync due to critical rate limit: repoName={}, pageCount={}",
                        safeNameWithOwner,
                        pageCount
                    );
                    break;
                }

                GHPullRequestConnection connection = response
                    .field("repository.pullRequests")
                    .toEntity(GHPullRequestConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Process the page within its own transaction to keep transactions short
                final Long repoId = repositoryId;
                PageSyncResult pageResult = transactionTemplate.execute(status -> {
                    // Re-fetch repository within transaction to ensure it's attached to session
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null) {
                        return new PageSyncResult(0, 0, 0);
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);
                    return processPullRequestPage(
                        connection,
                        context,
                        scopeId,
                        prsNeedingReviewPagination,
                        prsNeedingThreadPagination
                    );
                });

                if (pageResult != null) {
                    totalPRsSynced += pageResult.prsSynced();
                    totalReviewsSynced += pageResult.reviewsSynced();
                    totalReviewCommentsSynced += pageResult.reviewCommentsSynced();
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // For incremental sync: check if the oldest PR in this page is older than effectiveSyncTimestamp
                // PRs are ordered by updatedAt DESC, so the last item is the oldest
                if (incrementalSync && hasMore) {
                    var nodes = connection.getNodes();
                    if (!nodes.isEmpty()) {
                        var oldestPr = nodes.get(nodes.size() - 1);
                        OffsetDateTime oldestUpdatedAt = oldestPr.getUpdatedAt();
                        if (oldestUpdatedAt != null && oldestUpdatedAt.toInstant().isBefore(effectiveSyncTimestamp)) {
                            log.debug(
                                "Stopping incremental PR sync: oldestUpdatedAt={} is before effectiveSyncTimestamp={}",
                                oldestUpdatedAt,
                                effectiveSyncTimestamp
                            );
                            hasMore = false;
                            stoppedByIncrementalSync = true;
                        }
                    }
                }

                // Persist cursor checkpoint after each successful page (uses REQUIRES_NEW)
                if (syncTargetId != null && cursor != null && hasMore) {
                    persistCursorCheckpoint(syncTargetId, cursor);
                }

                // Reset retry counter after successful page
                retryAttempt = 0;
            } catch (InstallationNotFoundException e) {
                // Re-throw to abort the entire sync operation
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        // Transient error - retry with exponential backoff
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying PR sync after transient error: repoName={}, attempt={}, error={}",
                                safeNameWithOwner,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("PR sync interrupted during backoff: repoName={}", safeNameWithOwner);
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync PRs after {} retries: repoName={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            safeNameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    case RATE_LIMITED -> {
                        // Rate limited - wait for reset time if available, then retry
                        if (retryAttempt < MAX_RETRY_ATTEMPTS && classification.suggestedWait() != null) {
                            retryAttempt++;
                            long waitMs = Math.min(
                                classification.suggestedWait().toMillis(),
                                300_000 // Cap at 5 minutes
                            );
                            log.warn(
                                "Rate limited during PR sync, waiting: repoName={}, waitMs={}, attempt={}",
                                safeNameWithOwner,
                                waitMs,
                                retryAttempt
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("PR sync interrupted during rate limit wait: repoName={}", safeNameWithOwner);
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Aborting PR sync due to rate limiting: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    case NOT_FOUND -> {
                        // Resource not found - skip and continue
                        log.warn(
                            "Resource not found during PR sync, skipping: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    case AUTH_ERROR -> {
                        // Authentication error - abort sync
                        log.error(
                            "Aborting PR sync due to auth error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    case CLIENT_ERROR -> {
                        // Client error - abort sync
                        log.error(
                            "Aborting PR sync due to client error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    default -> {
                        // Unknown error - log and abort
                        log.error(
                            "Aborting PR sync due to unknown error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message(),
                            e
                        );
                        break;
                    }
                }
                break;
            }
        }

        // Fetch remaining reviews for PRs with >10 reviews (using cursor for efficient continuation)
        // Each call to syncRemainingReviews handles its own transactions
        if (!prsNeedingReviewPagination.isEmpty()) {
            log.debug(
                "Starting additional review fetch for PRs with pagination: repoName={}, prCount={}",
                safeNameWithOwner,
                prsNeedingReviewPagination.size()
            );
            for (PullRequestWithReviewCursor prWithCursor : prsNeedingReviewPagination) {
                int additionalReviews = reviewSyncService.syncRemainingReviews(
                    scopeId,
                    prWithCursor.pullRequest(),
                    prWithCursor.reviewCursor()
                );
                totalReviewsSynced += additionalReviews;
            }
        }

        // Fetch remaining review threads/comments for PRs with >10 threads (using cursor for efficient continuation)
        // Each call to syncRemainingThreads handles its own transactions
        if (!prsNeedingThreadPagination.isEmpty()) {
            log.debug(
                "Starting additional review thread fetch for PRs with pagination: repoName={}, prCount={}",
                safeNameWithOwner,
                prsNeedingThreadPagination.size()
            );
            for (PullRequestWithThreadCursor prWithCursor : prsNeedingThreadPagination) {
                int additionalComments = reviewCommentSyncService.syncRemainingThreads(
                    scopeId,
                    prWithCursor.pullRequest(),
                    prWithCursor.threadCursor()
                );
                totalReviewCommentsSynced += additionalComments;
            }
        }

        // Clear cursor on successful completion (uses REQUIRES_NEW)
        if (syncTargetId != null && !hasMore) {
            clearCursorCheckpoint(syncTargetId);
        }

        log.info(
            "Completed pull request sync: repoName={}, prCount={}, reviewCount={}, reviewCommentCount={}, prsWithReviewPagination={}, prsWithThreadPagination={}, resumed={}, incremental={}, stoppedByIncremental={}",
            safeNameWithOwner,
            totalPRsSynced,
            totalReviewsSynced,
            totalReviewCommentsSynced,
            prsNeedingReviewPagination.size(),
            prsNeedingThreadPagination.size(),
            resuming,
            incrementalSync,
            stoppedByIncrementalSync
        );
        return totalPRsSynced;
    }

    /**
     * Result of processing a page of pull requests.
     */
    private record PageSyncResult(int prsSynced, int reviewsSynced, int reviewCommentsSynced) {}

    /**
     * Processes a page of pull requests with their embedded reviews and review threads.
     */
    private PageSyncResult processPullRequestPage(
        GHPullRequestConnection connection,
        ProcessingContext context,
        Long scopeId,
        List<PullRequestWithReviewCursor> prsNeedingReviewPagination,
        List<PullRequestWithThreadCursor> prsNeedingThreadPagination
    ) {
        int prsSynced = 0;
        int reviewsSynced = 0;
        int reviewCommentsSynced = 0;

        for (var graphQlPullRequest : connection.getNodes()) {
            PullRequestWithReviewThreads prWithReviews = PullRequestWithReviewThreads.fromPullRequest(
                graphQlPullRequest
            );
            if (prWithReviews == null || prWithReviews.pullRequest() == null) {
                continue;
            }

            // Process the PR
            PullRequest entity = pullRequestProcessor.process(prWithReviews.pullRequest(), context);
            if (entity == null) {
                continue;
            }
            prsSynced++;

            // Process embedded reviews
            EmbeddedReviewsDTO embeddedReviews = prWithReviews.embeddedReviews();
            for (GitHubReviewDTO reviewDTO : embeddedReviews.reviews()) {
                if (reviewProcessor.process(reviewDTO, entity.getId()) != null) {
                    reviewsSynced++;
                }
            }

            // Track PRs that need additional review pagination (with cursor for efficient continuation)
            if (embeddedReviews.needsPagination()) {
                prsNeedingReviewPagination.add(new PullRequestWithReviewCursor(entity, embeddedReviews.endCursor()));
            }

            // Process embedded review threads and their comments
            EmbeddedReviewThreadsDTO embeddedThreads = prWithReviews.embeddedReviewThreads();
            for (GitHubReviewThreadDTO thread : embeddedThreads.threads()) {
                int commentsSynced = reviewCommentSyncService.processThread(thread, entity, scopeId);
                reviewCommentsSynced += commentsSynced;
            }

            // Track PRs that need additional thread pagination (with cursor for efficient continuation)
            if (embeddedThreads.needsPagination()) {
                prsNeedingThreadPagination.add(new PullRequestWithThreadCursor(entity, embeddedThreads.endCursor()));
            }
        }

        return new PageSyncResult(prsSynced, reviewsSynced, reviewCommentsSynced);
    }

    /**
     * Persists the cursor checkpoint in a new transaction.
     * This ensures the cursor is saved even if the main transaction is rolled back.
     * <p>
     * Uses programmatic transaction management with REQUIRES_NEW propagation
     * to avoid Spring proxy issues with self-invocation.
     */
    private void persistCursorCheckpoint(Long syncTargetId, String cursor) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            backfillStateProvider.updatePullRequestSyncCursor(syncTargetId, cursor);
            log.debug("Persisted PR sync cursor checkpoint: syncTargetId={}", syncTargetId);
        });
    }

    /**
     * Clears the cursor checkpoint in a new transaction.
     * Called when sync completes successfully to indicate no resume needed.
     * <p>
     * Uses programmatic transaction management with REQUIRES_NEW propagation
     * to avoid Spring proxy issues with self-invocation.
     */
    private void clearCursorCheckpoint(Long syncTargetId) {
        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTemplate.executeWithoutResult(status -> {
            backfillStateProvider.updatePullRequestSyncCursor(syncTargetId, null);
            log.debug("Cleared PR sync cursor checkpoint: syncTargetId={}", syncTargetId);
        });
    }
}

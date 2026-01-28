package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

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
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedCommentsDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.IssueWithComments;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
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
import org.springframework.graphql.client.GraphQlTransportException;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service for synchronizing GitHub issues via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubIssueProcessor.
 * <p>
 * Comments are fetched inline with issues (first 10 per issue) to avoid N+1 queries.
 * Only issues with more than 10 comments require additional API calls for pagination.
 * <p>
 * Supports checkpoint/cursor persistence for resumable sync operations.
 * When a sync target ID is provided, the pagination cursor is persisted after
 * each page, allowing sync to resume from where it left off if interrupted.
 */
@Service
public class GitHubIssueSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryIssues";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubIssueProcessor issueProcessor;
    private final GitHubIssueCommentProcessor commentProcessor;
    private final GitHubIssueCommentSyncService commentSyncService;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Retry configuration for transport-level errors during body streaming.
     * <p>
     * CRITICAL: WebClient ExchangeFilterFunction retries DO NOT cover body streaming errors.
     * PrematureCloseException occurs AFTER HTTP headers are received, during body consumption.
     * We must retry at this level using Mono.defer() to wrap the entire execute() call.
     */
    private static final int TRANSPORT_MAX_RETRIES = 3;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);
    private static final double JITTER_FACTOR = 0.5;

    /**
     * Container for issues that need additional comment pagination.
     */
    private record IssueWithCommentCursor(Issue issue, String commentCursor) {}

    public GitHubIssueSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubIssueProcessor issueProcessor,
        GitHubIssueCommentProcessor commentProcessor,
        GitHubIssueCommentSyncService commentSyncService,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.issueProcessor = issueProcessor;
        this.commentProcessor = commentProcessor;
        this.commentSyncService = commentSyncService;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all issues for a repository without cursor persistence.
     * <p>
     * This method is kept for backward compatibility. For resumable sync with
     * cursor persistence, use {@link #syncForRepository(Long, Long, Long, String)}.
     * <p>
     * Note: This method intentionally does NOT use @Transactional to avoid long-running
     * transactions. Each page of issues is processed in its own transaction.
     *
     * @param scopeId      the scope ID for authentication
     * @param repositoryId the repository ID to sync issues for
     * @return sync result containing status and count of issues synced
     */
    public SyncResult syncForRepository(Long scopeId, Long repositoryId) {
        return syncForRepository(scopeId, repositoryId, null, null, null);
    }

    /**
     * Synchronizes all issues for a repository with cursor persistence support.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId       the scope ID for authentication
     * @param repositoryId  the repository ID to sync issues for
     * @param syncTargetId  the sync target ID for cursor persistence (null to disable)
     * @param initialCursor the cursor to resume from (null to start from beginning)
     * @return sync result containing status and count of issues synced
     */
    public SyncResult syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor
    ) {
        return syncForRepository(scopeId, repositoryId, syncTargetId, initialCursor, null);
    }

    /**
     * Synchronizes issues for a repository with incremental sync support.
     * <p>
     * When {@code lastSyncTimestamp} is provided and incremental sync is enabled,
     * only issues updated after that timestamp are fetched using GitHub's filterBy.since parameter.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     * <p>
     * Note: This method intentionally does NOT use @Transactional to avoid long-running
     * transactions. Each page of issues is processed in its own transaction to keep
     * individual transactions short (seconds, not minutes) while maintaining data
     * consistency within each page.
     *
     * @param scopeId           the scope ID for authentication
     * @param repositoryId      the repository ID to sync issues for
     * @param syncTargetId      the sync target ID for cursor persistence (null to disable)
     * @param initialCursor     the cursor to resume from (null to start from beginning)
     * @param lastSyncTimestamp the timestamp of the last sync for incremental sync (null for full sync)
     * @return sync result containing status and count of issues synced
     */
    public SyncResult syncForRepository(
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
            log.debug("Skipped issue sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return SyncResult.completed(0);
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped issue sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return SyncResult.completed(0);
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        // Determine the 'since' parameter for incremental sync
        // Apply safety buffer to handle clock skew between server and GitHub
        OffsetDateTime sinceDateTime = null;
        boolean isIncrementalSync = false;
        if (syncProperties.incrementalSyncEnabled()) {
            if (lastSyncTimestamp != null) {
                // Subtract buffer to ensure items updated just before recorded timestamp are still fetched
                Instant bufferedTimestamp = lastSyncTimestamp.minus(syncProperties.incrementalSyncBuffer());
                sinceDateTime = bufferedTimestamp.atOffset(ZoneOffset.UTC);
                log.info(
                    "Starting incremental issue sync: repoName={}, since={}, buffer={}",
                    safeNameWithOwner,
                    sinceDateTime,
                    syncProperties.incrementalSyncBuffer()
                );
            } else {
                // First sync - use configured timeframe as fallback to limit initial data fetch
                sinceDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(syncSchedulerProperties.timeframeDays());
                log.info(
                    "Starting first issue sync with timeframe fallback: repoName={}, timeframeDays={}, since={}",
                    safeNameWithOwner,
                    syncSchedulerProperties.timeframeDays(),
                    sinceDateTime
                );
            }
            isIncrementalSync = true;
        }

        int totalIssuesSynced = 0;
        int totalCommentsSynced = 0;
        List<IssueWithCommentCursor> issuesNeedingCommentPagination = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        boolean resuming = initialCursor != null;
        final boolean incrementalSync = isIncrementalSync;
        SyncResult.Status abortReason = null; // null means completed successfully

        if (resuming) {
            log.info(
                "Resuming issue sync from checkpoint: repoName={}, cursor={}",
                safeNameWithOwner,
                initialCursor.substring(0, Math.min(20, initialCursor.length())) + "..."
            );
        }

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for issue sync: repoName={}, limit={}",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                // Build the GraphQL request with optional 'since' parameter for incremental sync.
                // Use Mono.defer() to wrap the entire execute() call so retries cover body streaming.
                // This is CRITICAL: WebClient ExchangeFilterFunction retries only cover the HTTP exchange,
                // not body consumption. PrematureCloseException occurs DURING body streaming.
                final String currentCursor = cursor;
                final int currentPage = pageCount;
                final OffsetDateTime sinceDt = sinceDateTime;

                ClientGraphQlResponse response = Mono.defer(() -> {
                    var requestBuilder = client
                        .documentName(QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor);

                    // Add 'since' parameter for incremental sync (filters by updatedAt >= since)
                    if (sinceDt != null) {
                        requestBuilder = requestBuilder.variable("since", sinceDt.toString());
                    }

                    return requestBuilder.execute();
                })
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying issue sync after transport error: repoName={}, page={}, attempt={}, error={}",
                                    safeNameWithOwner,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(timeout);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response: repoName={}, errors={}",
                        safeNameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response (per-scope tracking)
                graphQlClientProvider.trackRateLimit(scopeId, response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn(
                        "Aborting issue sync due to critical rate limit: repoName={}, pageCount={}",
                        safeNameWithOwner,
                        pageCount
                    );
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }

                GHIssueConnection connection = response.field("repository.issues").toEntity(GHIssueConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Process the page within its own transaction to keep transactions short
                final Long repoId = repositoryId;
                PageResult pageResult = transactionTemplate.execute(status -> {
                    // Re-fetch repository within transaction to ensure it's attached to session
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null) {
                        return new PageResult(0, 0);
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);
                    return processIssuePage(connection, context, issuesNeedingCommentPagination);
                });

                if (pageResult != null) {
                    totalIssuesSynced += pageResult.issueCount;
                    totalCommentsSynced += pageResult.commentCount;
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

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
                                "Retrying issue sync after transient error: repoName={}, attempt={}, error={}",
                                safeNameWithOwner,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Issue sync interrupted during backoff: repoName={}", safeNameWithOwner);
                                abortReason = SyncResult.Status.ABORTED_ERROR;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync issues after {} retries: repoName={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
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
                                "Rate limited during issue sync, waiting: repoName={}, waitMs={}, attempt={}",
                                safeNameWithOwner,
                                waitMs,
                                retryAttempt
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn(
                                    "Issue sync interrupted during rate limit wait: repoName={}",
                                    safeNameWithOwner
                                );
                                abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Aborting issue sync due to rate limiting: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                        break;
                    }
                    case NOT_FOUND -> {
                        // Resource not found - skip and continue
                        log.warn(
                            "Resource not found during issue sync, skipping: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case AUTH_ERROR -> {
                        // Authentication error - abort sync
                        log.error(
                            "Aborting issue sync due to auth error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    case CLIENT_ERROR -> {
                        // Client error - abort sync
                        log.error(
                            "Aborting issue sync due to client error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                    default -> {
                        // Unknown error - log and abort
                        log.error(
                            "Aborting issue sync due to unknown error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message(),
                            e
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                        break;
                    }
                }
                break;
            }
        }

        // Fetch remaining comments for issues with >10 comments (using cursor for efficient continuation)
        // Each call to syncRemainingComments handles its own transactions
        if (!issuesNeedingCommentPagination.isEmpty()) {
            log.debug(
                "Starting additional comment fetch for issues with pagination: repoName={}, issueCount={}",
                safeNameWithOwner,
                issuesNeedingCommentPagination.size()
            );
            for (IssueWithCommentCursor issueWithCursor : issuesNeedingCommentPagination) {
                int additionalComments = commentSyncService.syncRemainingComments(
                    scopeId,
                    issueWithCursor.issue(),
                    issueWithCursor.commentCursor()
                );
                totalCommentsSynced += additionalComments;
            }
        }

        // Clear cursor on successful completion (uses REQUIRES_NEW)
        // Only clear if sync completed without abort
        if (syncTargetId != null && !hasMore && abortReason == null) {
            clearCursorCheckpoint(syncTargetId);
        }

        // Determine the final result status
        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;

        log.info(
            "Completed issue sync: repoName={}, issueCount={}, commentCount={}, issuesWithPagination={}, scopeId={}, resumed={}, incremental={}, status={}",
            safeNameWithOwner,
            totalIssuesSynced,
            totalCommentsSynced,
            issuesNeedingCommentPagination.size(),
            scopeId,
            resuming,
            incrementalSync,
            finalStatus
        );
        return new SyncResult(finalStatus, totalIssuesSynced);
    }

    /**
     * Result container for page processing.
     */
    private record PageResult(int issueCount, int commentCount) {}

    /**
     * Processes a page of issues with embedded comments and returns the counts.
     * Also populates the list of issues that need additional comment pagination.
     */
    private PageResult processIssuePage(
        GHIssueConnection connection,
        ProcessingContext context,
        List<IssueWithCommentCursor> issuesNeedingPagination
    ) {
        int issuesSynced = 0;
        int commentsSynced = 0;

        for (var graphQlIssue : connection.getNodes()) {
            IssueWithComments issueWithComments = IssueWithComments.fromIssue(graphQlIssue);
            if (issueWithComments == null || issueWithComments.issue() == null) {
                continue;
            }

            // Process the issue
            Issue entity = issueProcessor.process(issueWithComments.issue(), context);
            if (entity == null) {
                continue;
            }
            issuesSynced++;

            // Process embedded comments
            EmbeddedCommentsDTO embeddedComments = issueWithComments.embeddedComments();
            for (GitHubCommentDTO commentDTO : embeddedComments.comments()) {
                if (commentProcessor.process(commentDTO, entity.getNumber(), context) != null) {
                    commentsSynced++;
                }
            }

            // Track issues that need additional comment pagination (with cursor for efficient continuation)
            if (embeddedComments.needsPagination()) {
                issuesNeedingPagination.add(new IssueWithCommentCursor(entity, embeddedComments.endCursor()));
            }
        }

        return new PageResult(issuesSynced, commentsSynced);
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
            backfillStateProvider.updateIssueSyncCursor(syncTargetId, cursor);
            log.debug("Persisted issue sync cursor checkpoint: syncTargetId={}", syncTargetId);
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
            backfillStateProvider.updateIssueSyncCursor(syncTargetId, null);
            log.debug("Cleared issue sync cursor checkpoint: syncTargetId={}", syncTargetId);
        });
    }

    // ========================================================================
    // Transport Error Detection
    // ========================================================================

    /**
     * Determines if an exception is a transport-level error that should be retried.
     * <p>
     * Transport errors occur at the network/connection level during body streaming:
     * <ul>
     *   <li>{@code GraphQlTransportException}: Spring GraphQL wrapper for transport failures</li>
     *   <li>{@code PrematureCloseException}: Connection closed during response body streaming</li>
     *   <li>Connection reset/abort exceptions</li>
     * </ul>
     * <p>
     * IMPORTANT: These errors occur AFTER HTTP headers are received (200 OK) but DURING
     * body consumption. WebClient ExchangeFilterFunction retries do NOT catch these.
     *
     * @param throwable the exception to check
     * @return true if this is a retryable transport error
     */
    private boolean isTransportError(Throwable throwable) {
        // GraphQlTransportException is Spring GraphQL's wrapper for transport failures
        if (throwable instanceof GraphQlTransportException) {
            return true;
        }

        // Walk the cause chain for wrapped transport errors
        Throwable cause = throwable;
        while (cause != null) {
            String className = cause.getClass().getName();

            // PrematureCloseException: Connection closed during response streaming
            if (className.contains("PrematureCloseException")) {
                return true;
            }

            // Other reactor-netty transport errors
            if (className.contains("AbortedException") || className.contains("ConnectionResetException")) {
                return true;
            }

            // Check for IOException indicating connection issues
            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (
                        lower.contains("connection reset") ||
                        lower.contains("broken pipe") ||
                        lower.contains("connection abort") ||
                        lower.contains("premature") ||
                        lower.contains("stream closed")
                    ) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        }
        return false;
    }
}

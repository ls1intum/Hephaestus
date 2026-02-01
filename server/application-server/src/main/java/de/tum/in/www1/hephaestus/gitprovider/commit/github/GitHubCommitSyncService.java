package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubCommitDTO;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommitHistoryConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Service for synchronizing GitHub Commits via GraphQL API.
 * <p>
 * Fetches commit history from the default branch of a repository.
 * Commits are identified by SHA, not database ID, so the upsert pattern
 * uses (sha, repository_id) as the composite key.
 * <p>
 * Supports incremental sync by filtering commits by "since" timestamp.
 * <p>
 * Supports checkpoint/cursor persistence for resumable sync operations.
 * When a sync target ID is provided, the pagination cursor is persisted after
 * each page, allowing sync to resume from where it left off if interrupted.
 */
@Service
public class GitHubCommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryCommits";

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Retry configuration for transport-level errors during body streaming. */
    private static final int TRANSPORT_MAX_RETRIES = 2;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofMillis(500);

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubCommitProcessor commitProcessor;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubCommitSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubCommitProcessor commitProcessor,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commitProcessor = commitProcessor;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes commits for a repository without cursor persistence.
     *
     * @param scopeId      the scope ID for authentication
     * @param repositoryId the repository ID to sync commits for
     * @return sync result containing status and count of commits synced
     */
    public SyncResult syncForRepository(Long scopeId, Long repositoryId) {
        return syncForRepository(scopeId, repositoryId, null, null, null);
    }

    /**
     * Synchronizes commits for a repository with optional time filter.
     *
     * @param scopeId           the scope ID for authentication
     * @param repositoryId      the repository ID to sync commits for
     * @param since             only sync commits after this timestamp (null for all)
     * @return sync result containing status and count of commits synced
     */
    public SyncResult syncForRepository(Long scopeId, Long repositoryId, @Nullable Instant since) {
        return syncForRepository(scopeId, repositoryId, since, null, null);
    }

    /**
     * Synchronizes commits for a repository with cursor persistence support.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId       the scope ID for authentication
     * @param repositoryId  the repository ID to sync commits for
     * @param since         only sync commits after this timestamp (null for all)
     * @param syncTargetId  the sync target ID for cursor persistence (null to disable)
     * @param initialCursor the cursor to resume from (null to start from beginning)
     * @return sync result containing status and count of commits synced
     */
    public SyncResult syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Instant since,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor
    ) {
        // Fetch repository outside of transaction
        Repository repository = transactionTemplate.execute(status ->
            repositoryRepository.findById(repositoryId).orElse(null)
        );
        if (repository == null) {
            log.debug("Skipped commit sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return SyncResult.completed(0);
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped commit sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return SyncResult.completed(0);
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        // Convert since to ISO8601 format for GitHub API
        String sinceStr =
            since != null ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(since.atOffset(ZoneOffset.UTC)) : null;

        int totalCommitsSynced = 0;
        String cursor = initialCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        boolean resuming = initialCursor != null;
        SyncResult.Status abortReason = null;

        if (resuming) {
            log.info(
                "Resuming commit sync from checkpoint: repoName={}, cursor={}",
                safeNameWithOwner,
                initialCursor.substring(0, Math.min(20, initialCursor.length())) + "..."
            );
        }

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for commit sync: repoName={}, limit={}",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;
                final String sinceFinal = sinceStr;

                ClientGraphQlResponse response = Mono.defer(() -> {
                    var requestBuilder = client
                        .documentName(QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor);

                    // Add since filter for incremental sync
                    if (sinceFinal != null) {
                        requestBuilder = requestBuilder.variable("since", sinceFinal);
                    }

                    return requestBuilder.execute();
                })
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying commit sync after transport error: repoName={}, page={}, attempt={}, error={}",
                                    safeNameWithOwner,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure())
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

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting commit sync due to critical rate limit: repoName={}", safeNameWithOwner);
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }

                // Navigate to commit history: repository.defaultBranchRef.target.history
                GHCommitHistoryConnection history = response
                    .field("repository.defaultBranchRef.target.history")
                    .toEntity(GHCommitHistoryConnection.class);

                if (history == null || history.getNodes() == null || history.getNodes().isEmpty()) {
                    // No commits found - could be empty repo or no default branch
                    log.debug("No commits found: repoName={}", safeNameWithOwner);
                    break;
                }

                // Process page within transaction
                final Long repoId = repositoryId;
                Integer pageSynced = transactionTemplate.execute(status -> {
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null) {
                        return 0;
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);
                    return processCommitPage(history, context);
                });

                if (pageSynced != null) {
                    totalCommitsSynced += pageSynced;
                }

                GHPageInfo pageInfo = history.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Persist cursor checkpoint after each successful page (uses REQUIRES_NEW)
                if (syncTargetId != null && cursor != null && hasMore) {
                    persistCursorCheckpoint(syncTargetId, cursor);
                }

                // Reset retry counter after successful page
                retryAttempt = 0;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        // Transient error - retry with exponential backoff
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying commit sync after transient error: repoName={}, attempt={}, error={}",
                                safeNameWithOwner,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Commit sync interrupted during backoff: repoName={}", safeNameWithOwner);
                                abortReason = SyncResult.Status.ABORTED_ERROR;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync commits after {} retries: repoName={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
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
                                "Rate limited during commit sync, waiting: repoName={}, waitMs={}, attempt={}",
                                safeNameWithOwner,
                                waitMs,
                                retryAttempt
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn(
                                    "Commit sync interrupted during rate limit wait: repoName={}",
                                    safeNameWithOwner
                                );
                                abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Aborting commit sync due to rate limiting: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    }
                    default -> {
                        log.error(
                            "Failed to sync commits: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message(),
                            e
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                }
                break;
            }
        }

        // Clear cursor on successful completion (uses REQUIRES_NEW)
        // Only clear if sync completed without abort
        if (syncTargetId != null && !hasMore && abortReason == null) {
            clearCursorCheckpoint(syncTargetId);
        }

        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;
        log.info(
            "Completed commit sync: repoName={}, commitCount={}, incremental={}, resumed={}, status={}",
            safeNameWithOwner,
            totalCommitsSynced,
            since != null,
            resuming,
            finalStatus
        );
        return new SyncResult(finalStatus, totalCommitsSynced);
    }

    /**
     * Processes a page of commits.
     */
    private int processCommitPage(GHCommitHistoryConnection history, ProcessingContext context) {
        int commitsSynced = 0;

        for (var graphQlCommit : history.getNodes()) {
            GitHubCommitDTO commitDTO = GitHubCommitDTO.fromCommit(graphQlCommit);
            if (commitDTO == null) {
                continue;
            }

            Commit commit = commitProcessor.process(commitDTO, context);
            if (commit != null) {
                commitsSynced++;
            }
        }

        return commitsSynced;
    }

    // ========================================================================
    // Cursor Checkpoint Persistence
    // ========================================================================

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
            backfillStateProvider.updateCommitSyncCursor(syncTargetId, cursor);
            log.debug("Persisted commit sync cursor checkpoint: syncTargetId={}", syncTargetId);
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
            backfillStateProvider.updateCommitSyncCursor(syncTargetId, null);
            log.debug("Cleared commit sync cursor checkpoint: syncTargetId={}", syncTargetId);
        });
    }

    // ========================================================================
    // Transport Error Detection
    // ========================================================================

    /**
     * Determines if an exception is a transport-level error that should be retried.
     *
     * @param throwable the exception to check
     * @return true if this is a retryable transport error
     */
    private boolean isTransportError(Throwable throwable) {
        if (throwable instanceof GraphQlTransportException) {
            return true;
        }

        Throwable cause = throwable;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (
                className.contains("PrematureCloseException") ||
                className.contains("AbortedException") ||
                className.contains("ConnectionResetException")
            ) {
                return true;
            }

            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (
                        lower.contains("connection reset") ||
                        lower.contains("broken pipe") ||
                        lower.contains("premature")
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

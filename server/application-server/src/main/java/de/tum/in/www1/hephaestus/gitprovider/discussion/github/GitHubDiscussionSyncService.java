package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

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
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.GitHubDiscussionCommentProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Service for synchronizing GitHub Discussions via GraphQL API.
 * <p>
 * Discussions are fetched with embedded comments (first 10 per discussion).
 * Discussions with more comments require additional pagination calls.
 * <p>
 * Supports checkpoint/cursor persistence for resumable sync operations.
 * When a sync target ID is provided, the pagination cursor is persisted after
 * each page, allowing sync to resume from where it left off if interrupted.
 */
@Service
public class GitHubDiscussionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiscussionSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryDiscussions";
    private static final String COMMENTS_QUERY_DOCUMENT = "GetDiscussionComments";
    private static final String REPLIES_QUERY_DOCUMENT = "GetCommentReplies";
    private static final int EMBEDDED_COMMENTS_COUNT = 10;
    private static final int EMBEDDED_REPLIES_COUNT = 10;

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Retry configuration for transport-level errors during body streaming. */
    private static final int TRANSPORT_MAX_RETRIES = 3;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);
    private static final double JITTER_FACTOR = 0.5;

    /**
     * Container for discussions that need additional comment pagination.
     * <p>
     * Stores primitive IDs instead of the Discussion entity to avoid
     * LazyInitializationException when accessing lazy relationships
     * after the original transaction has ended.
     */
    private record DiscussionWithCommentCursor(
        Long discussionId,
        int discussionNumber,
        Long repositoryId,
        String commentCursor
    ) {}

    /**
     * Container for comments that need additional reply pagination.
     * <p>
     * Stores the comment's GitHub node ID (for GraphQL query), database ID,
     * and the discussion ID for context.
     */
    private record CommentWithReplyCursor(
        String commentNodeId,
        Long commentDatabaseId,
        Long discussionId,
        String replyCursor
    ) {}

    /**
     * Result from nested pagination sync operations.
     * <p>
     * Unlike the main sync which can resume via cursor, nested pagination
     * must complete or fail. This record tracks both the count and success status
     * so failures can be propagated to the final sync result.
     *
     * @param count   number of items synced
     * @param success true if pagination completed without errors
     */
    private record NestedSyncResult(int count, boolean success) {
        static NestedSyncResult success(int count) {
            return new NestedSyncResult(count, true);
        }

        static NestedSyncResult failure(int count) {
            return new NestedSyncResult(count, false);
        }
    }

    private final RepositoryRepository repositoryRepository;
    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository commentRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final GitHubDiscussionCommentProcessor commentProcessor;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubDiscussionSyncService(
        RepositoryRepository repositoryRepository,
        DiscussionRepository discussionRepository,
        DiscussionCommentRepository commentRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubDiscussionProcessor discussionProcessor,
        GitHubDiscussionCommentProcessor commentProcessor,
        BackfillStateProvider backfillStateProvider,
        TransactionTemplate transactionTemplate,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.discussionRepository = discussionRepository;
        this.commentRepository = commentRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.discussionProcessor = discussionProcessor;
        this.commentProcessor = commentProcessor;
        this.backfillStateProvider = backfillStateProvider;
        this.transactionTemplate = transactionTemplate;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all discussions for a repository without cursor persistence.
     * <p>
     * This method is kept for backward compatibility. For resumable sync with
     * cursor persistence, use {@link #syncForRepository(Long, Long, Long, String)}.
     *
     * @param scopeId      the scope ID for authentication
     * @param repositoryId the repository ID to sync discussions for
     * @return sync result containing status and count of discussions synced
     */
    public SyncResult syncForRepository(Long scopeId, Long repositoryId) {
        return syncForRepository(scopeId, repositoryId, null, null);
    }

    /**
     * Synchronizes all discussions for a repository with cursor persistence support.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId       the scope ID for authentication
     * @param repositoryId  the repository ID to sync discussions for
     * @param syncTargetId  the sync target ID for cursor persistence (null to disable)
     * @param initialCursor the cursor to resume from (null to start from beginning)
     * @return sync result containing status and count of discussions synced
     */
    public SyncResult syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor
    ) {
        // Delegate to full method with no stop timestamp (historical backfill mode)
        return syncForRepository(scopeId, repositoryId, syncTargetId, initialCursor, null);
    }

    /**
     * Synchronizes discussions for a repository with cursor persistence and incremental sync support.
     * <p>
     * <b>Historical Backfill vs Incremental Sync:</b>
     * <ul>
     *   <li>Historical backfill: {@code stopAfterTimestamp} is null - fetches ALL discussions</li>
     *   <li>Incremental sync: {@code stopAfterTimestamp} is set - stops when reaching discussions
     *       with updatedAt <= stopAfterTimestamp (discussions are ordered by UPDATED_AT DESC)</li>
     * </ul>
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId             the scope ID for authentication
     * @param repositoryId        the repository ID to sync discussions for
     * @param syncTargetId        the sync target ID for cursor persistence (null to disable)
     * @param initialCursor       the cursor to resume from (null to start from beginning)
     * @param stopAfterTimestamp  stop syncing when reaching discussions updated before this time
     *                            (null for historical backfill - sync all discussions)
     * @return sync result containing status and count of discussions synced
     */
    public SyncResult syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor,
        @Nullable java.time.Instant stopAfterTimestamp
    ) {
        // Fetch repository outside of transaction
        Repository repository = transactionTemplate.execute(status ->
            repositoryRepository.findById(repositoryId).orElse(null)
        );
        if (repository == null) {
            log.debug("Skipped discussion sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return SyncResult.completed(0);
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped discussion sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return SyncResult.completed(0);
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
        Duration timeout = syncProperties.graphqlTimeout();

        int totalDiscussionsSynced = 0;
        int totalCommentsSynced = 0;
        List<DiscussionWithCommentCursor> discussionsNeedingCommentPagination = new ArrayList<>();
        List<CommentWithReplyCursor> commentsNeedingReplyPagination = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        boolean resuming = initialCursor != null;
        SyncResult.Status abortReason = null;
        // For incremental sync: track pages fetched after hitting stop condition
        // to catch same-timestamp items that might span page boundaries
        int pagesAfterStopCondition = 0;
        final int safetyPagesAfterStop = 1;

        if (resuming) {
            log.info(
                "Resuming discussion sync from checkpoint: repoName={}, cursor={}",
                safeNameWithOwner,
                initialCursor.substring(0, Math.min(20, initialCursor.length())) + "..."
            );
        }

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.error(
                    "Reached maximum pagination limit for discussion sync: repoName={}, limit={}, syncedSoFar={}. " +
                    "Cursor will be preserved for resume. This indicates an unusually large repository.",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES,
                    totalDiscussionsSynced
                );
                // CRITICAL: Set abort reason to prevent cursor from being cleared
                // This allows sync to resume from this point on next run
                abortReason = SyncResult.Status.ABORTED_ERROR;
                break;
            }

            try {
                final String currentCursor = cursor;
                final int currentPage = pageCount;
                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying discussion sync after transport error: repoName={}, page={}, attempt={}, error={}",
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
                    abortReason = SyncResult.Status.ABORTED_ERROR;
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting discussion sync due to critical rate limit: repoName={}", safeNameWithOwner);
                    abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    break;
                }

                GHDiscussionConnection connection = response
                    .field("repository.discussions")
                    .toEntity(GHDiscussionConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Process page within transaction
                final Long repoId = repositoryId;
                final List<DiscussionWithCommentCursor> pageDiscussionsNeedingPagination = new ArrayList<>();
                final List<CommentWithReplyCursor> pageCommentsNeedingReplyPagination = new ArrayList<>();
                final java.time.Instant finalStopAfterTimestamp = stopAfterTimestamp;
                PageResult pageResult = transactionTemplate.execute(status -> {
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null) {
                        return new PageResult(0, 0, false);
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);
                    return processDiscussionPage(
                        connection,
                        context,
                        pageDiscussionsNeedingPagination,
                        pageCommentsNeedingReplyPagination,
                        finalStopAfterTimestamp
                    );
                });

                if (pageResult != null) {
                    totalDiscussionsSynced += pageResult.discussionCount;
                    totalCommentsSynced += pageResult.commentCount;

                    // Incremental sync: handle stop condition with safety margin
                    // Continue fetching for 'safetyPagesAfterStop' more pages to catch
                    // same-timestamp items that might span page boundaries
                    if (pageResult.reachedStopTime()) {
                        pagesAfterStopCondition++;
                        if (pagesAfterStopCondition > safetyPagesAfterStop) {
                            log.info(
                                "Incremental sync complete: repoName={}, reachedPreviouslySyncedContent=true, " +
                                "safetyPagesProcessed={}",
                                safeNameWithOwner,
                                pagesAfterStopCondition - 1
                            );
                            hasMore = false;
                            // Still need to process the pending comment/reply pagination
                        } else {
                            log.debug(
                                "Incremental sync: reached stop condition, processing safety page {}/{}",
                                pagesAfterStopCondition,
                                safetyPagesAfterStop
                            );
                        }
                    }
                }
                discussionsNeedingCommentPagination.addAll(pageDiscussionsNeedingPagination);
                commentsNeedingReplyPagination.addAll(pageCommentsNeedingReplyPagination);

                GHPageInfo pageInfo = connection.getPageInfo();
                if (hasMore) { // Only check page info if we haven't stopped due to incremental sync
                    hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                }
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // CRITICAL: Defensive check for null cursor with hasNextPage=true
                // This can happen due to GitHub API bugs and would cause infinite loop
                if (hasMore && cursor == null) {
                    log.error(
                        "GraphQL API returned hasNextPage=true but null endCursor: repoName={}, page={}. " +
                        "Aborting to prevent infinite loop.",
                        safeNameWithOwner,
                        pageCount
                    );
                    abortReason = SyncResult.Status.ABORTED_ERROR;
                    break;
                }

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
                                "Retrying discussion sync after transient error: repoName={}, attempt={}, error={}",
                                safeNameWithOwner,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Discussion sync interrupted during backoff: repoName={}", safeNameWithOwner);
                                abortReason = SyncResult.Status.ABORTED_ERROR;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync discussions after {} retries: repoName={}, error={}",
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
                                "Rate limited during discussion sync, waiting: repoName={}, waitMs={}, attempt={}",
                                safeNameWithOwner,
                                waitMs,
                                retryAttempt
                            );
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn(
                                    "Discussion sync interrupted during rate limit wait: repoName={}",
                                    safeNameWithOwner
                                );
                                abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Aborting discussion sync due to rate limiting: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                    }
                    case NOT_FOUND -> {
                        // Resource not found - skip and continue
                        log.warn(
                            "Resource not found during discussion sync, skipping: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    case AUTH_ERROR -> {
                        // Authentication error - abort sync
                        log.error(
                            "Aborting discussion sync due to auth error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    case CLIENT_ERROR -> {
                        // Client error - abort sync
                        log.error(
                            "Aborting discussion sync due to client error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    default -> {
                        // Unknown error - log and abort
                        log.error(
                            "Aborting discussion sync due to unknown error: repoName={}, error={}",
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

        // Fetch remaining comments for discussions with >10 comments
        // Also collects more comments needing reply pagination
        // Track nested failures to propagate to final status
        boolean nestedPaginationFailed = false;
        if (!discussionsNeedingCommentPagination.isEmpty()) {
            log.debug(
                "Starting additional comment fetch for discussions with pagination: repoName={}, discussionCount={}",
                safeNameWithOwner,
                discussionsNeedingCommentPagination.size()
            );
            for (DiscussionWithCommentCursor discussionWithCursor : discussionsNeedingCommentPagination) {
                List<CommentWithReplyCursor> additionalCommentsNeedingReplies = new ArrayList<>();
                NestedSyncResult commentResult = syncRemainingComments(
                    scopeId,
                    discussionWithCursor.discussionId(),
                    discussionWithCursor.discussionNumber(),
                    discussionWithCursor.repositoryId(),
                    discussionWithCursor.commentCursor(),
                    ownerAndName,
                    client,
                    timeout,
                    additionalCommentsNeedingReplies
                );
                totalCommentsSynced += commentResult.count();
                if (!commentResult.success()) {
                    nestedPaginationFailed = true;
                    log.warn(
                        "Comment pagination failed for discussion: discussionNumber={}, partialCount={}",
                        discussionWithCursor.discussionNumber(),
                        commentResult.count()
                    );
                }
                commentsNeedingReplyPagination.addAll(additionalCommentsNeedingReplies);
            }
        }

        // Fetch remaining replies for comments with >10 replies
        if (!commentsNeedingReplyPagination.isEmpty()) {
            log.debug(
                "Starting additional reply fetch for comments with pagination: repoName={}, commentCount={}",
                safeNameWithOwner,
                commentsNeedingReplyPagination.size()
            );
            for (CommentWithReplyCursor commentWithCursor : commentsNeedingReplyPagination) {
                NestedSyncResult replyResult = syncRemainingReplies(
                    scopeId,
                    commentWithCursor.commentNodeId(),
                    commentWithCursor.commentDatabaseId(),
                    commentWithCursor.discussionId(),
                    commentWithCursor.replyCursor(),
                    client,
                    timeout
                );
                totalCommentsSynced += replyResult.count();
                if (!replyResult.success()) {
                    nestedPaginationFailed = true;
                    log.warn(
                        "Reply pagination failed for comment: commentNodeId={}, partialCount={}",
                        commentWithCursor.commentNodeId(),
                        replyResult.count()
                    );
                }
            }
        }

        // If nested pagination failed, mark the sync as incomplete
        if (nestedPaginationFailed && abortReason == null) {
            abortReason = SyncResult.Status.ABORTED_ERROR;
        }

        // Clear cursor on successful completion (uses REQUIRES_NEW)
        // Only clear if sync completed without abort
        if (syncTargetId != null && !hasMore && abortReason == null) {
            clearCursorCheckpoint(syncTargetId);
        }

        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;
        log.info(
            "Completed discussion sync: repoName={}, discussionCount={}, commentCount={}, discussionsWithCommentPagination={}, commentsWithReplyPagination={}, resumed={}, status={}",
            safeNameWithOwner,
            totalDiscussionsSynced,
            totalCommentsSynced,
            discussionsNeedingCommentPagination.size(),
            commentsNeedingReplyPagination.size(),
            resuming,
            finalStatus
        );
        return new SyncResult(finalStatus, totalDiscussionsSynced);
    }

    /**
     * Result container for page processing.
     *
     * @param discussionCount number of discussions synced
     * @param commentCount    number of comments synced (including replies)
     * @param reachedStopTime true if we encountered a discussion older than the stop timestamp
     */
    private record PageResult(int discussionCount, int commentCount, boolean reachedStopTime) {}

    /**
     * Processes a page of discussions with embedded comments and replies.
     * <p>
     * Also populates the lists of discussions/comments that need additional pagination:
     * - discussionsNeedingPagination: discussions with >10 top-level comments
     * - commentsNeedingReplyPagination: comments with >10 replies
     *
     * @param connection                     the GraphQL connection
     * @param context                        the processing context
     * @param discussionsNeedingPagination   output list for discussions with >10 comments
     * @param commentsNeedingReplyPagination output list for comments with >10 replies
     * @param stopAfterTimestamp             stop processing when reaching discussions updated before this
     *                                       (null for historical backfill)
     * @return page result with counts and whether stop time was reached
     */
    private PageResult processDiscussionPage(
        GHDiscussionConnection connection,
        ProcessingContext context,
        List<DiscussionWithCommentCursor> discussionsNeedingPagination,
        List<CommentWithReplyCursor> commentsNeedingReplyPagination,
        @Nullable java.time.Instant stopAfterTimestamp
    ) {
        int discussionsSynced = 0;
        int commentsSynced = 0;
        boolean reachedStopTime = false;

        // Map to track node ID -> processed comment for reply threading within this page
        Map<String, DiscussionComment> nodeIdToComment = new HashMap<>();

        for (var graphQlDiscussion : connection.getNodes()) {
            GitHubDiscussionDTO discussionDTO = GitHubDiscussionDTO.fromDiscussion(graphQlDiscussion);
            if (discussionDTO == null) {
                continue;
            }

            // Incremental sync stopping condition: if this discussion was last updated
            // before our stop timestamp, we've caught up with previously synced data
            // (discussions are ordered by UPDATED_AT DESC from GitHub)
            if (stopAfterTimestamp != null && discussionDTO.updatedAt() != null
                    && !discussionDTO.updatedAt().isAfter(stopAfterTimestamp)) {
                reachedStopTime = true;
                log.debug(
                    "Incremental sync: reached previously synced discussion, stopping: discussionNumber={}, updatedAt={}, stopAfter={}",
                    discussionDTO.number(),
                    discussionDTO.updatedAt(),
                    stopAfterTimestamp
                );
                // Still process this discussion (it might have updates we haven't seen)
                // but signal to stop after this page
            }

            Discussion discussion = discussionProcessor.process(discussionDTO, context);
            if (discussion == null) {
                continue;
            }
            discussionsSynced++;

            // Process embedded comments AND their replies
            GHDiscussionCommentConnection commentConn = graphQlDiscussion.getComments();
            if (commentConn != null && commentConn.getNodes() != null) {
                // Use the version that includes replies
                List<GitHubDiscussionCommentDTO> allCommentDTOs =
                    GitHubDiscussionCommentDTO.fromDiscussionCommentConnectionWithReplies(commentConn);

                for (GitHubDiscussionCommentDTO commentDTO : allCommentDTOs) {
                    DiscussionComment comment = commentProcessor.process(commentDTO, discussion, context);
                    if (comment != null) {
                        commentsSynced++;
                        // Track by node ID for reply threading
                        if (commentDTO.nodeId() != null) {
                            nodeIdToComment.put(commentDTO.nodeId(), comment);
                        }
                    }
                }

                // Resolve reply threading (second pass)
                for (GitHubDiscussionCommentDTO commentDTO : allCommentDTOs) {
                    if (commentDTO.replyToNodeId() != null && commentDTO.nodeId() != null) {
                        DiscussionComment comment = nodeIdToComment.get(commentDTO.nodeId());
                        DiscussionComment parent = nodeIdToComment.get(commentDTO.replyToNodeId());
                        if (comment != null && parent != null) {
                            commentProcessor.resolveParentComment(comment, parent);
                        } else if (comment != null && parent == null) {
                            // Cross-page threading: parent may be from a previous page
                            // Try to resolve from database
                            commentProcessor.resolveParentCommentByNodeId(comment, commentDTO.replyToNodeId());
                        }
                    }
                }

                // Update answer comment if this discussion has one
                if (discussionDTO.answerComment() != null && discussionDTO.answerComment().nodeId() != null) {
                    DiscussionComment answerComment = nodeIdToComment.get(discussionDTO.answerComment().nodeId());
                    if (answerComment != null) {
                        discussion.setAnswerComment(answerComment);
                        discussionRepository.save(discussion);
                    }
                }

                // Track discussions that need additional comment pagination
                GHPageInfo commentPageInfo = commentConn.getPageInfo();
                if (commentPageInfo != null && Boolean.TRUE.equals(commentPageInfo.getHasNextPage())) {
                    String commentEndCursor = commentPageInfo.getEndCursor();
                    if (commentEndCursor != null) {
                        discussionsNeedingPagination.add(
                            new DiscussionWithCommentCursor(
                                discussion.getId(),
                                discussion.getNumber(),
                                context.repository().getId(),
                                commentEndCursor
                            )
                        );
                    } else {
                        log.error(
                            "GraphQL API returned hasNextPage=true but null endCursor for comment pagination: " +
                            "discussionNumber={}. Skipping additional comment fetch to prevent infinite loop.",
                            discussion.getNumber()
                        );
                    }
                }

                // Track comments that need additional reply pagination
                for (GHDiscussionComment graphQlComment : commentConn.getNodes()) {
                    GHPageInfo replyPageInfo = GitHubDiscussionCommentDTO.getReplyPageInfo(graphQlComment);
                    if (replyPageInfo != null && Boolean.TRUE.equals(replyPageInfo.getHasNextPage())) {
                        String replyEndCursor = replyPageInfo.getEndCursor();
                        if (replyEndCursor != null) {
                            commentsNeedingReplyPagination.add(
                                new CommentWithReplyCursor(
                                    graphQlComment.getId(),
                                    graphQlComment.getDatabaseId() != null ? graphQlComment.getDatabaseId().longValue() : null,
                                    discussion.getId(),
                                    replyEndCursor
                                )
                            );
                        } else {
                            log.error(
                                "GraphQL API returned hasNextPage=true but null endCursor for reply pagination: " +
                                "commentId={}. Skipping additional reply fetch to prevent infinite loop.",
                                graphQlComment.getId()
                            );
                        }
                    }
                }
            }
        }

        return new PageResult(discussionsSynced, commentsSynced, reachedStopTime);
    }

    /**
     * Synchronizes remaining comments for a discussion, starting from the given cursor.
     * <p>
     * This method is called when a discussion has more than 10 comments (the embedded limit
     * in GetRepositoryDiscussions query). It continues pagination from where the embedded
     * comments left off, avoiding re-fetching already synced comments.
     * <p>
     * Also processes replies embedded in each comment and tracks comments with >10 replies
     * that need additional pagination.
     * <p>
     * Uses primitive IDs instead of entity references to avoid LazyInitializationException
     * when the original transaction has ended. The discussion is re-fetched inside each
     * page's transaction.
     *
     * @param scopeId                          the scope ID for authentication
     * @param discussionId                     the discussion database ID
     * @param discussionNumber                 the discussion number (for GraphQL query)
     * @param repositoryId                     the repository ID (for re-fetching)
     * @param startCursor                      the pagination cursor to start from
     * @param ownerAndName                     the parsed repository owner and name
     * @param client                           the GraphQL client
     * @param timeout                          the request timeout
     * @param commentsNeedingReplyPagination   output list for comments with >10 replies
     * @return result containing count and success status
     */
    private NestedSyncResult syncRemainingComments(
        Long scopeId,
        Long discussionId,
        int discussionNumber,
        Long repositoryId,
        String startCursor,
        RepositoryOwnerAndName ownerAndName,
        HttpGraphQlClient client,
        Duration timeout,
        List<CommentWithReplyCursor> commentsNeedingReplyPagination
    ) {
        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;
        boolean encounteredError = false;

        log.debug(
            "Starting remaining comment sync: discussionNumber={}, startCursor={}",
            discussionNumber,
            startCursor != null ? startCursor.substring(0, Math.min(20, startCursor.length())) + "..." : "null"
        );

        // Map to track node ID -> processed comment for reply threading within this pagination
        Map<String, DiscussionComment> nodeIdToComment = new HashMap<>();
        int retryAttempt = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.error(
                    "Reached maximum pagination limit for remaining comment sync: discussionNumber={}, limit={}, syncedSoFar={}. " +
                    "This discussion has an unusually large number of comments.",
                    discussionNumber,
                    MAX_PAGINATION_PAGES,
                    totalSynced
                );
                encounteredError = true;
                break;
            }

            try {
                final String currentCursor = cursor;
                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(COMMENTS_QUERY_DOCUMENT)
                        .variable("owner", ownerAndName.owner())
                        .variable("name", ownerAndName.name())
                        .variable("discussionNumber", discussionNumber)
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying comment sync after transport error: discussionNumber={}, attempt={}, error={}",
                                    discussionNumber,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure())
                    )
                    .block(timeout);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for remaining comment sync: discussionNumber={}, errors={}",
                        discussionNumber,
                        response != null ? response.getErrors() : "null"
                    );
                    encounteredError = true;
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn(
                        "Aborting remaining comment sync due to critical rate limit: discussionNumber={}",
                        discussionNumber
                    );
                    encounteredError = true;
                    break;
                }

                GHDiscussionCommentConnection connection = response
                    .field("repository.discussion.comments")
                    .toEntity(GHDiscussionCommentConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Process comments within transaction
                // Re-fetch entities inside the transaction to avoid LazyInitializationException
                final Long finalDiscussionId = discussionId;
                final Long finalRepositoryId = repositoryId;
                Integer pageSynced = transactionTemplate.execute(status -> {
                    Repository repo = repositoryRepository.findById(finalRepositoryId).orElse(null);
                    if (repo == null) {
                        return 0;
                    }
                    Discussion discussion = discussionRepository.findById(finalDiscussionId).orElse(null);
                    if (discussion == null) {
                        return 0;
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);

                    int synced = 0;
                    // Use the version that includes replies
                    List<GitHubDiscussionCommentDTO> allCommentDTOs =
                        GitHubDiscussionCommentDTO.fromDiscussionCommentConnectionWithReplies(connection);

                    for (GitHubDiscussionCommentDTO commentDTO : allCommentDTOs) {
                        DiscussionComment comment = commentProcessor.process(commentDTO, discussion, context);
                        if (comment != null) {
                            synced++;
                            if (commentDTO.nodeId() != null) {
                                nodeIdToComment.put(commentDTO.nodeId(), comment);
                            }
                        }
                    }

                    // Resolve reply threading
                    for (GitHubDiscussionCommentDTO commentDTO : allCommentDTOs) {
                        if (commentDTO.replyToNodeId() != null && commentDTO.nodeId() != null) {
                            DiscussionComment comment = nodeIdToComment.get(commentDTO.nodeId());
                            DiscussionComment parent = nodeIdToComment.get(commentDTO.replyToNodeId());
                            if (comment != null && parent != null) {
                                commentProcessor.resolveParentComment(comment, parent);
                            } else if (comment != null && parent == null) {
                                // Cross-page threading: parent may be from a previous page
                                commentProcessor.resolveParentCommentByNodeId(comment, commentDTO.replyToNodeId());
                            }
                        }
                    }

                    return synced;
                });

                if (pageSynced != null) {
                    totalSynced += pageSynced;
                }

                // Track comments that need additional reply pagination
                for (GHDiscussionComment graphQlComment : connection.getNodes()) {
                    GHPageInfo replyPageInfo = GitHubDiscussionCommentDTO.getReplyPageInfo(graphQlComment);
                    if (replyPageInfo != null && Boolean.TRUE.equals(replyPageInfo.getHasNextPage())) {
                        String replyEndCursor = replyPageInfo.getEndCursor();
                        if (replyEndCursor != null) {
                            commentsNeedingReplyPagination.add(
                                new CommentWithReplyCursor(
                                    graphQlComment.getId(),
                                    graphQlComment.getDatabaseId() != null ? graphQlComment.getDatabaseId().longValue() : null,
                                    discussionId,
                                    replyEndCursor
                                )
                            );
                        } else {
                            log.error(
                                "GraphQL API returned hasNextPage=true but null endCursor for reply pagination in syncRemainingComments: " +
                                "commentId={}. Skipping additional reply fetch to prevent infinite loop.",
                                graphQlComment.getId()
                            );
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Defensive check for null cursor with hasNextPage=true
                if (hasMore && cursor == null) {
                    log.error(
                        "GraphQL API returned hasNextPage=true but null endCursor for comments: discussionNumber={}. " +
                        "Aborting to prevent infinite loop.",
                        discussionNumber
                    );
                    encounteredError = true;
                    break;
                }

                // Reset retry counter after successful page
                retryAttempt = 0;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying comment sync after transient error: discussionNumber={}, attempt={}, error={}",
                                discussionNumber,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Comment sync interrupted during backoff: discussionNumber={}", discussionNumber);
                                encounteredError = true;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync remaining comments after {} retries: discussionNumber={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            discussionNumber,
                            classification.message()
                        );
                    }
                    case RATE_LIMITED -> log.warn(
                        "Rate limited during comment sync: discussionNumber={}, error={}",
                        discussionNumber,
                        classification.message()
                    );
                    case NOT_FOUND -> log.warn(
                        "Resource not found during comment sync: discussionNumber={}, error={}",
                        discussionNumber,
                        classification.message()
                    );
                    case AUTH_ERROR -> log.error(
                        "Authentication error during comment sync: discussionNumber={}, error={}",
                        discussionNumber,
                        classification.message()
                    );
                    case CLIENT_ERROR -> log.error(
                        "Client error during comment sync: discussionNumber={}, error={}",
                        discussionNumber,
                        classification.message()
                    );
                    default -> log.error(
                        "Unexpected error during comment sync: discussionNumber={}, error={}",
                        discussionNumber,
                        classification.message(),
                        e
                    );
                }
                encounteredError = true;
                break;
            }
        }

        log.debug(
            "Completed remaining comment sync: discussionNumber={}, additionalComments={}, success={}",
            discussionNumber,
            totalSynced,
            !encounteredError
        );
        return encounteredError ? NestedSyncResult.failure(totalSynced) : NestedSyncResult.success(totalSynced);
    }

    /**
     * Synchronizes remaining replies for a comment, starting from the given cursor.
     * <p>
     * This method is called when a comment has more than 10 replies. It continues
     * pagination from where the embedded replies left off.
     *
     * @param scopeId          the scope ID for authentication
     * @param commentNodeId    the comment's GitHub node ID (for GraphQL query)
     * @param commentDbId      the comment's database ID
     * @param discussionId     the discussion database ID (for context)
     * @param startCursor      the pagination cursor to start from
     * @param client           the GraphQL client
     * @param timeout          the request timeout
     * @return result containing count and success status
     */
    private NestedSyncResult syncRemainingReplies(
        Long scopeId,
        String commentNodeId,
        Long commentDbId,
        Long discussionId,
        String startCursor,
        HttpGraphQlClient client,
        Duration timeout
    ) {
        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;
        boolean encounteredError = false;

        log.debug(
            "Starting remaining reply sync: commentNodeId={}, startCursor={}",
            commentNodeId != null ? commentNodeId.substring(0, Math.min(20, commentNodeId.length())) + "..." : "null",
            startCursor != null ? startCursor.substring(0, Math.min(20, startCursor.length())) + "..." : "null"
        );

        int retryAttempt = 0;

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.error(
                    "Reached maximum pagination limit for remaining reply sync: commentNodeId={}, limit={}, syncedSoFar={}. " +
                    "This comment has an unusually large number of replies.",
                    commentNodeId,
                    MAX_PAGINATION_PAGES,
                    totalSynced
                );
                encounteredError = true;
                break;
            }

            try {
                final String currentCursor = cursor;
                ClientGraphQlResponse response = Mono.defer(() ->
                    client
                        .documentName(REPLIES_QUERY_DOCUMENT)
                        .variable("commentId", commentNodeId)
                        .variable("first", DEFAULT_PAGE_SIZE)
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(this::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying reply sync after transport error: commentNodeId={}, attempt={}, error={}",
                                    commentNodeId,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure())
                    )
                    .block(timeout);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for remaining reply sync: commentNodeId={}, errors={}",
                        commentNodeId,
                        response != null ? response.getErrors() : "null"
                    );
                    encounteredError = true;
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn(
                        "Aborting remaining reply sync due to critical rate limit: commentNodeId={}",
                        commentNodeId
                    );
                    encounteredError = true;
                    break;
                }

                GHDiscussionCommentConnection replyConnection = response
                    .field("node.replies")
                    .toEntity(GHDiscussionCommentConnection.class);

                if (replyConnection == null || replyConnection.getNodes() == null || replyConnection.getNodes().isEmpty()) {
                    break;
                }

                // Process replies within transaction
                final Long finalDiscussionId = discussionId;
                final Long finalCommentDbId = commentDbId;
                Integer pageSynced = transactionTemplate.execute(status -> {
                    Discussion discussion = discussionRepository.findById(finalDiscussionId).orElse(null);
                    if (discussion == null) {
                        return 0;
                    }

                    DiscussionComment parentComment = null;
                    if (finalCommentDbId != null) {
                        parentComment = commentRepository.findById(finalCommentDbId).orElse(null);
                    }

                    ProcessingContext context = ProcessingContext.forSync(scopeId, discussion.getRepository());

                    int synced = 0;
                    for (GHDiscussionComment graphQlReply : replyConnection.getNodes()) {
                        GitHubDiscussionCommentDTO replyDTO = GitHubDiscussionCommentDTO.fromDiscussionComment(graphQlReply);
                        if (replyDTO != null) {
                            DiscussionComment reply = commentProcessor.process(replyDTO, discussion, context);
                            if (reply != null) {
                                synced++;
                                // Set parent directly since we know it
                                if (parentComment != null && reply.getParentComment() == null) {
                                    commentProcessor.resolveParentComment(reply, parentComment);
                                }
                            }
                        }
                    }

                    return synced;
                });

                if (pageSynced != null) {
                    totalSynced += pageSynced;
                }

                GHPageInfo pageInfo = replyConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Defensive check for null cursor with hasNextPage=true
                if (hasMore && cursor == null) {
                    log.error(
                        "GraphQL API returned hasNextPage=true but null endCursor for replies: commentNodeId={}. " +
                        "Aborting to prevent infinite loop.",
                        commentNodeId
                    );
                    encounteredError = true;
                    break;
                }

                // Reset retry counter after successful page
                retryAttempt = 0;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                Category category = classification.category();

                switch (category) {
                    case RETRYABLE -> {
                        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                            retryAttempt++;
                            log.warn(
                                "Retrying reply sync after transient error: commentNodeId={}, attempt={}, error={}",
                                commentNodeId,
                                retryAttempt,
                                classification.message()
                            );
                            try {
                                ExponentialBackoff.sleep(retryAttempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Reply sync interrupted during backoff: commentNodeId={}", commentNodeId);
                                encounteredError = true;
                                break;
                            }
                            continue; // Retry the same page
                        }
                        log.error(
                            "Failed to sync remaining replies after {} retries: commentNodeId={}, error={}",
                            MAX_RETRY_ATTEMPTS,
                            commentNodeId,
                            classification.message()
                        );
                    }
                    case RATE_LIMITED -> log.warn(
                        "Rate limited during reply sync: commentNodeId={}, error={}",
                        commentNodeId,
                        classification.message()
                    );
                    case NOT_FOUND -> log.warn(
                        "Resource not found during reply sync: commentNodeId={}, error={}",
                        commentNodeId,
                        classification.message()
                    );
                    case AUTH_ERROR -> log.error(
                        "Authentication error during reply sync: commentNodeId={}, error={}",
                        commentNodeId,
                        classification.message()
                    );
                    case CLIENT_ERROR -> log.error(
                        "Client error during reply sync: commentNodeId={}, error={}",
                        commentNodeId,
                        classification.message()
                    );
                    default -> log.error(
                        "Unexpected error during reply sync: commentNodeId={}, error={}",
                        commentNodeId,
                        classification.message(),
                        e
                    );
                }
                encounteredError = true;
                break;
            }
        }

        log.debug(
            "Completed remaining reply sync: commentNodeId={}, additionalReplies={}, success={}",
            commentNodeId,
            totalSynced,
            !encounteredError
        );
        return encounteredError ? NestedSyncResult.failure(totalSynced) : NestedSyncResult.success(totalSynced);
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
            backfillStateProvider.updateDiscussionSyncCursor(syncTargetId, cursor);
            log.debug("Persisted discussion sync cursor checkpoint: syncTargetId={}", syncTargetId);
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
            backfillStateProvider.updateDiscussionSyncCursor(syncTargetId, null);
            log.debug("Cleared discussion sync cursor checkpoint: syncTargetId={}", syncTargetId);
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

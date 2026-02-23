package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DISCUSSION_SYNC_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.adaptPageSize;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator.GraphQlClassificationContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlConnectionOverflowDetector;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.GitHubDiscussionCommentProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.client.ClientGraphQlResponse;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubDiscussionSyncService {

    private static final String QUERY_DOCUMENT = "GetRepositoryDiscussions";
    private static final String COMMENTS_QUERY_DOCUMENT = "GetDiscussionComments";

    /** Maximum number of retry attempts for transient failures. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

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

    private final RepositoryRepository repositoryRepository;
    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubDiscussionProcessor discussionProcessor;
    private final GitHubDiscussionCommentProcessor commentProcessor;
    private final BackfillStateProvider backfillStateProvider;
    private final TransactionTemplate transactionTemplate;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final GitHubGraphQlSyncCoordinator graphQlSyncHelper;

    /**
     * Synchronizes all discussions for a repository without cursor persistence.
     * <p>
     * This method is kept for backward compatibility. For resumable sync with
     * cursor persistence, use {@link #syncForRepository(Long, Long, Long, String, Instant)}.
     *
     * @param scopeId      the scope ID for authentication
     * @param repositoryId the repository ID to sync discussions for
     * @return sync result containing status and count of discussions synced
     */
    public SyncResult syncForRepository(Long scopeId, Long repositoryId) {
        return syncForRepository(scopeId, repositoryId, null, null, null);
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
        return syncForRepository(scopeId, repositoryId, syncTargetId, initialCursor, null);
    }

    /**
     * Synchronizes discussions for a repository with incremental sync support.
     * <p>
     * Discussions are ordered by {@code UPDATED_AT DESC} in the GraphQL query.
     * When {@code lastSyncTimestamp} is provided and incremental sync is enabled,
     * pagination stops early once the oldest discussion on a page has an
     * {@code updatedAt} before the effective sync timestamp (mirroring PR sync).
     * All discussions on each fetched page are still processed — the timestamp
     * is a <b>stop-condition</b>, not a skip-filter.
     * <p>
     * For the first sync ({@code lastSyncTimestamp == null}), ALL discussions are
     * fetched with no time filter so historical data is not silently dropped.
     * <p>
     * When {@code syncTargetId} is provided, the pagination cursor is persisted after
     * each successfully processed page. This allows sync to resume from where it left
     * off if the process is interrupted (e.g., crash, timeout, deployment).
     * <p>
     * On successful completion, the cursor is cleared to indicate sync finished.
     *
     * @param scopeId           the scope ID for authentication
     * @param repositoryId      the repository ID to sync discussions for
     * @param syncTargetId      the sync target ID for cursor persistence (null to disable)
     * @param initialCursor     the cursor to resume from (null to start from beginning)
     * @param lastSyncTimestamp the timestamp of the last sync for incremental sync (null for full sync)
     * @return sync result containing status and count of discussions synced
     */
    public SyncResult syncForRepository(
        Long scopeId,
        Long repositoryId,
        @Nullable Long syncTargetId,
        @Nullable String initialCursor,
        @Nullable Instant lastSyncTimestamp
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

        // Determine the effective timestamp for incremental sync stop-condition.
        // Discussions are ordered by UPDATED_AT DESC, so we stop pagination when the
        // oldest discussion on a page has updatedAt < effectiveSyncTimestamp.
        // For first sync (lastSyncTimestamp == null): fetch ALL discussions (no filter).
        // For incremental sync: use lastSyncTimestamp minus safety buffer.
        Instant effectiveSyncTimestamp = null;
        boolean isIncrementalSync = false;
        if (syncProperties.incrementalSyncEnabled() && lastSyncTimestamp != null) {
            effectiveSyncTimestamp = lastSyncTimestamp.minus(syncProperties.incrementalSyncBuffer());
            isIncrementalSync = true;
            log.info(
                "Starting incremental discussion sync: repoName={}, since={}, buffer={}",
                safeNameWithOwner,
                effectiveSyncTimestamp,
                syncProperties.incrementalSyncBuffer()
            );
        } else {
            log.info("Starting full discussion sync (no timeframe filter): repoName={}", safeNameWithOwner);
        }
        final boolean incrementalSync = isIncrementalSync;

        int totalDiscussionsSynced = 0;
        int totalCommentsSynced = 0;
        List<DiscussionWithCommentCursor> discussionsNeedingCommentPagination = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;
        boolean resuming = initialCursor != null;
        boolean stoppedByIncrementalSync = false;
        SyncResult.Status abortReason = null;
        int reportedTotalCount = -1;

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
                log.warn(
                    "Reached maximum pagination limit for discussion sync: repoName={}, limit={}",
                    safeNameWithOwner,
                    MAX_PAGINATION_PAGES
                );
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
                        .variable(
                            "first",
                            adaptPageSize(
                                DISCUSSION_SYNC_PAGE_SIZE,
                                graphQlClientProvider.getRateLimitRemaining(scopeId)
                            )
                        )
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
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
                    var classification = graphQlSyncHelper.classifyGraphQlErrors(response);
                    if (classification != null) {
                        boolean shouldRetry = graphQlSyncHelper.handleGraphQlClassification(
                            new GraphQlClassificationContext(
                                classification,
                                retryAttempt,
                                MAX_RETRY_ATTEMPTS,
                                "discussion sync",
                                "repoName",
                                safeNameWithOwner,
                                log
                            )
                        );
                        if (shouldRetry) {
                            retryAttempt++;
                            continue;
                        }
                    }
                    abortReason = SyncResult.Status.ABORTED_ERROR;
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    boolean canContinue = graphQlSyncHelper.waitForRateLimitIfNeeded(
                        scopeId,
                        "discussion sync",
                        "repoName",
                        safeNameWithOwner,
                        log
                    );
                    if (!canContinue) {
                        abortReason = SyncResult.Status.ABORTED_RATE_LIMIT;
                        break;
                    }
                }

                GHDiscussionConnection connection = response
                    .field("repository.discussions")
                    .toEntity(GHDiscussionConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Capture reported total count from the first page
                if (reportedTotalCount < 0) {
                    reportedTotalCount = connection.getTotalCount();
                }

                // Process page within transaction
                final Long repoId = repositoryId;
                final List<DiscussionWithCommentCursor> pageDiscussionsNeedingPagination = new ArrayList<>();
                PageResult pageResult = transactionTemplate.execute(status -> {
                    Repository repo = repositoryRepository.findById(repoId).orElse(null);
                    if (repo == null) {
                        return new PageResult(0, 0);
                    }
                    ProcessingContext context = ProcessingContext.forSync(scopeId, repo);
                    return processDiscussionPage(connection, context, pageDiscussionsNeedingPagination);
                });

                if (pageResult != null) {
                    totalDiscussionsSynced += pageResult.discussionCount;
                    totalCommentsSynced += pageResult.commentCount;
                }
                discussionsNeedingCommentPagination.addAll(pageDiscussionsNeedingPagination);

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // For incremental sync: check if the oldest discussion on this page
                // is older than effectiveSyncTimestamp. Discussions are ordered by
                // updatedAt DESC, so the last item is the oldest on the page.
                if (incrementalSync && hasMore && effectiveSyncTimestamp != null) {
                    var nodes = connection.getNodes();
                    if (!nodes.isEmpty()) {
                        var oldestDiscussion = nodes.get(nodes.size() - 1);
                        OffsetDateTime oldestUpdatedAt = oldestDiscussion.getUpdatedAt();
                        if (oldestUpdatedAt != null && oldestUpdatedAt.toInstant().isBefore(effectiveSyncTimestamp)) {
                            log.debug(
                                "Stopping incremental discussion sync: oldestUpdatedAt={} is before effectiveSyncTimestamp={}",
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

                // Throttle between pagination requests to avoid hammering GitHub
                // This reduces 502/504 errors caused by rapid-fire complex queries
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("Discussion sync interrupted during throttle: repoName={}", safeNameWithOwner);
                        break;
                    }
                }
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
                        log.warn(
                            "Resource not found during discussion sync, skipping: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    case AUTH_ERROR -> {
                        log.error(
                            "Aborting discussion sync due to auth error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    case CLIENT_ERROR -> {
                        log.error(
                            "Aborting discussion sync due to client error: repoName={}, error={}",
                            safeNameWithOwner,
                            classification.message()
                        );
                        abortReason = SyncResult.Status.ABORTED_ERROR;
                    }
                    case UNKNOWN -> {
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

        // Detect if pagination was incomplete (reported total vs actually synced)
        if (reportedTotalCount >= 0) {
            GraphQlConnectionOverflowDetector.check(
                "discussions",
                totalDiscussionsSynced,
                reportedTotalCount,
                safeNameWithOwner
            );
        }

        // Fetch remaining comments for discussions with >10 comments
        if (!discussionsNeedingCommentPagination.isEmpty()) {
            log.debug(
                "Starting additional comment fetch for discussions with pagination: repoName={}, discussionCount={}",
                safeNameWithOwner,
                discussionsNeedingCommentPagination.size()
            );
            for (DiscussionWithCommentCursor discussionWithCursor : discussionsNeedingCommentPagination) {
                int additionalComments = syncRemainingComments(
                    scopeId,
                    discussionWithCursor.discussionId(),
                    discussionWithCursor.discussionNumber(),
                    discussionWithCursor.repositoryId(),
                    discussionWithCursor.commentCursor(),
                    ownerAndName,
                    client,
                    timeout
                );
                totalCommentsSynced += additionalComments;
            }

            // Resolve answer comment FK for discussions where the answer arrived
            // via comment pagination (beyond the first embedded page of 10 comments).
            // The initial processDiscussionPage only sets answerComment from its
            // nodeIdToComment map which is limited to the first page of comments.
            resolveAnswerCommentsAfterPagination(discussionsNeedingCommentPagination);
        }

        // Clear cursor on successful completion (uses REQUIRES_NEW)
        // Only clear if sync completed without abort (including incremental stop)
        if (syncTargetId != null && (!hasMore || stoppedByIncrementalSync) && abortReason == null) {
            clearCursorCheckpoint(syncTargetId);
        }

        SyncResult.Status finalStatus = abortReason != null ? abortReason : SyncResult.Status.COMPLETED;
        log.info(
            "Completed discussion sync: repoName={}, discussionCount={}, commentCount={}, discussionsWithPagination={}, resumed={}, stoppedByIncrementalSync={}, status={}",
            safeNameWithOwner,
            totalDiscussionsSynced,
            totalCommentsSynced,
            discussionsNeedingCommentPagination.size(),
            resuming,
            stoppedByIncrementalSync,
            finalStatus
        );
        return new SyncResult(finalStatus, totalDiscussionsSynced);
    }

    /**
     * Result container for page processing.
     */
    private record PageResult(int discussionCount, int commentCount) {}

    /**
     * Processes a page of discussions with embedded comments.
     * Also populates the list of discussions that need additional comment pagination.
     *
     * @param connection                  the GraphQL discussion connection
     * @param context                     the processing context
     * @param discussionsNeedingPagination list to populate with discussions needing comment pagination
     */
    private PageResult processDiscussionPage(
        GHDiscussionConnection connection,
        ProcessingContext context,
        List<DiscussionWithCommentCursor> discussionsNeedingPagination
    ) {
        int discussionsSynced = 0;
        int commentsSynced = 0;

        // Map to track node ID -> processed comment for reply threading
        Map<String, DiscussionComment> nodeIdToComment = new HashMap<>();

        for (var graphQlDiscussion : connection.getNodes()) {
            GitHubDiscussionDTO discussionDTO = GitHubDiscussionDTO.fromDiscussion(graphQlDiscussion);
            if (discussionDTO == null) {
                continue;
            }

            Discussion discussion = discussionProcessor.process(discussionDTO, context);
            if (discussion == null) {
                continue;
            }
            discussionsSynced++;

            // Process embedded comments
            GHDiscussionCommentConnection commentConn = graphQlDiscussion.getComments();
            if (commentConn != null && commentConn.getNodes() != null) {
                List<GitHubDiscussionCommentDTO> commentDTOs =
                    GitHubDiscussionCommentDTO.fromDiscussionCommentConnection(commentConn);

                for (GitHubDiscussionCommentDTO commentDTO : commentDTOs) {
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
                for (GitHubDiscussionCommentDTO commentDTO : commentDTOs) {
                    if (commentDTO.replyToNodeId() != null && commentDTO.nodeId() != null) {
                        DiscussionComment comment = nodeIdToComment.get(commentDTO.nodeId());
                        DiscussionComment parent = nodeIdToComment.get(commentDTO.replyToNodeId());
                        if (comment != null && parent != null) {
                            commentProcessor.resolveParentComment(comment, parent);
                        }
                    }
                }

                // Detect overflow in reply sub-connections
                for (var node : commentConn.getNodes()) {
                    if (node != null && node.getReplies() != null) {
                        var repliesPageInfo = node.getReplies().getPageInfo();
                        if (repliesPageInfo != null && Boolean.TRUE.equals(repliesPageInfo.getHasNextPage())) {
                            GraphQlConnectionOverflowDetector.check(
                                "discussionComment.replies",
                                node.getReplies().getNodes() != null ? node.getReplies().getNodes().size() : 0,
                                true,
                                "discussionNumber=" + graphQlDiscussion.getNumber() + ", commentId=" + node.getId()
                            );
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
                // Store primitive IDs to avoid LazyInitializationException after transaction ends
                GHPageInfo commentPageInfo = commentConn.getPageInfo();
                if (commentPageInfo != null && commentPageInfo.getHasNextPage()) {
                    discussionsNeedingPagination.add(
                        new DiscussionWithCommentCursor(
                            discussion.getId(),
                            discussion.getNumber(),
                            context.repository().getId(),
                            commentPageInfo.getEndCursor()
                        )
                    );
                }
            }
        }

        return new PageResult(discussionsSynced, commentsSynced);
    }

    /**
     * Synchronizes remaining comments for a discussion, starting from the given cursor.
     * <p>
     * This method is called when a discussion has more than 10 comments (the embedded limit
     * in GetRepositoryDiscussions query). It continues pagination from where the embedded
     * comments left off, avoiding re-fetching already synced comments.
     * <p>
     * Uses primitive IDs instead of entity references to avoid LazyInitializationException
     * when the original transaction has ended. The discussion is re-fetched inside each
     * page's transaction.
     *
     * @param scopeId          the scope ID for authentication
     * @param discussionId     the discussion database ID
     * @param discussionNumber the discussion number (for GraphQL query)
     * @param repositoryId     the repository ID (for re-fetching)
     * @param startCursor      the pagination cursor to start from (from embedded comments)
     * @param ownerAndName     the parsed repository owner and name
     * @param client           the GraphQL client
     * @param timeout          the request timeout
     * @return number of additional comments synced
     */
    private int syncRemainingComments(
        Long scopeId,
        Long discussionId,
        int discussionNumber,
        Long repositoryId,
        String startCursor,
        RepositoryOwnerAndName ownerAndName,
        HttpGraphQlClient client,
        Duration timeout
    ) {
        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;

        log.debug(
            "Starting remaining comment sync: discussionNumber={}, startCursor={}",
            discussionNumber,
            startCursor != null ? startCursor.substring(0, Math.min(20, startCursor.length())) + "..." : "null"
        );

        // Map to track node ID -> processed comment for reply threading within this pagination
        Map<String, DiscussionComment> nodeIdToComment = new HashMap<>();

        while (hasMore) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for remaining comment sync: discussionNumber={}, limit={}",
                    discussionNumber,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                ClientGraphQlResponse response = client
                    .documentName(COMMENTS_QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("discussionNumber", discussionNumber)
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(timeout);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for remaining comment sync: discussionNumber={}, errors={}",
                        discussionNumber,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn(
                        "Aborting remaining comment sync due to critical rate limit: discussionNumber={}",
                        discussionNumber
                    );
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
                    List<GitHubDiscussionCommentDTO> commentDTOs =
                        GitHubDiscussionCommentDTO.fromDiscussionCommentConnection(connection);

                    for (GitHubDiscussionCommentDTO commentDTO : commentDTOs) {
                        DiscussionComment comment = commentProcessor.process(commentDTO, discussion, context);
                        if (comment != null) {
                            synced++;
                            if (commentDTO.nodeId() != null) {
                                nodeIdToComment.put(commentDTO.nodeId(), comment);
                            }
                        }
                    }

                    // Resolve reply threading
                    for (GitHubDiscussionCommentDTO commentDTO : commentDTOs) {
                        if (commentDTO.replyToNodeId() != null && commentDTO.nodeId() != null) {
                            DiscussionComment comment = nodeIdToComment.get(commentDTO.nodeId());
                            DiscussionComment parent = nodeIdToComment.get(commentDTO.replyToNodeId());
                            // Note: If parent is not in this page, we skip - it may have been
                            // processed in the embedded comments during main sync
                            if (comment != null && parent != null) {
                                commentProcessor.resolveParentComment(comment, parent);
                            }
                        }
                    }

                    return synced;
                });

                if (pageSynced != null) {
                    totalSynced += pageSynced;
                }

                // Detect overflow in reply sub-connections
                if (connection.getNodes() != null) {
                    for (var node : connection.getNodes()) {
                        if (node != null && node.getReplies() != null) {
                            var repliesPageInfo = node.getReplies().getPageInfo();
                            if (repliesPageInfo != null && Boolean.TRUE.equals(repliesPageInfo.getHasNextPage())) {
                                GraphQlConnectionOverflowDetector.check(
                                    "discussionComment.replies",
                                    node.getReplies().getNodes() != null ? node.getReplies().getNodes().size() : 0,
                                    true,
                                    "discussionNumber=" + discussionNumber + ", commentId=" + node.getId()
                                );
                            }
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && pageInfo.getHasNextPage();
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

                // Throttle between pagination requests to avoid hammering GitHub
                if (hasMore && !syncProperties.paginationThrottle().isZero()) {
                    try {
                        Thread.sleep(syncProperties.paginationThrottle().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug(
                            "Remaining comment sync interrupted during throttle: discussionNumber={}",
                            discussionNumber
                        );
                        break;
                    }
                }
            } catch (InstallationNotFoundException e) {
                // Re-throw to abort the entire sync operation
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                log.error(
                    "Failed to sync remaining comments: discussionNumber={}, error={}",
                    discussionNumber,
                    classification.message(),
                    e
                );
                break;
            }
        }

        log.debug(
            "Completed remaining comment sync: discussionNumber={}, additionalComments={}",
            discussionNumber,
            totalSynced
        );
        return totalSynced;
    }

    /**
     * Resolves the answer comment FK for discussions whose answer comment was
     * fetched via pagination (beyond the first embedded page of 10 comments).
     * <p>
     * During {@link #processDiscussionPage}, the answer comment is looked up
     * in a {@code nodeIdToComment} map that only contains comments from the
     * first embedded page. If the answer is the 11th+ top-level comment, it
     * arrives via {@link #syncRemainingComments} and has {@code is_answer=true}
     * on the comment entity, but the discussion's {@code answer_comment_id} FK
     * remains {@code null}.
     * <p>
     * This method checks each paginated discussion and resolves the FK by
     * querying for the comment marked as the answer.
     */
    private void resolveAnswerCommentsAfterPagination(
        List<DiscussionWithCommentCursor> discussionsNeedingCommentPagination
    ) {
        for (DiscussionWithCommentCursor dCursor : discussionsNeedingCommentPagination) {
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository.findById(dCursor.discussionId()).orElse(null);
                if (discussion == null) {
                    return;
                }
                // Only resolve if the discussion has an answer but no FK set yet
                if (discussion.getAnswerChosenAt() != null && discussion.getAnswerComment() == null) {
                    discussionCommentRepository
                        .findByDiscussionIdAndIsAnswerTrue(dCursor.discussionId())
                        .ifPresent(answerComment -> {
                            discussion.setAnswerComment(answerComment);
                            discussionRepository.save(discussion);
                            log.debug(
                                "Resolved answer comment FK after pagination: discussionNumber={}, commentId={}",
                                dCursor.discussionNumber(),
                                answerComment.getId()
                            );
                        });
                }
            });
        }
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
}

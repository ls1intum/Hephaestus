package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils.isNotFoundError;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncHelper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request reviews via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubPullRequestReviewProcessor.
 * <p>
 * GraphQL fetching is non-transactional; persistence is done per-page in
 * {@code REQUIRES_NEW} transactions via self-proxy to isolate deadlock
 * failures and avoid poisoned-transaction retries.
 */
@Service
public class GitHubPullRequestReviewSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewSyncService.class);
    private static final String QUERY_DOCUMENT = "GetPullRequestReviews";
    private static final String REVIEW_COMMENTS_QUERY_DOCUMENT = "GetReviewComments";

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestReviewProcessor reviewProcessor;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final GitHubGraphQlSyncHelper graphQlSyncHelper;
    private final GitHubPullRequestReviewSyncService self;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int MAX_DEADLOCK_RETRIES = 3;

    public GitHubPullRequestReviewSyncService(
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestReviewProcessor reviewProcessor,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier,
        GitHubGraphQlSyncHelper graphQlSyncHelper,
        @Lazy GitHubPullRequestReviewSyncService self
    ) {
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.reviewProcessor = reviewProcessor;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
        this.graphQlSyncHelper = graphQlSyncHelper;
        this.self = self;
    }

    /**
     * Synchronizes all reviews for all pull requests in a repository.
     * <p>
     * Uses streaming to avoid loading all pull requests into memory at once.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync reviews for
     * @return number of reviews synced
     */
    @Transactional
    public int syncForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.debug("Skipped review sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        AtomicInteger totalSynced = new AtomicInteger(0);
        AtomicInteger prCount = new AtomicInteger(0);

        try (Stream<PullRequest> prStream = pullRequestRepository.streamAllByRepository_Id(repositoryId)) {
            prStream.forEach(pullRequest -> {
                totalSynced.addAndGet(syncForPullRequest(scopeId, pullRequest));
                prCount.incrementAndGet();
            });
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        if (prCount.get() == 0) {
            log.debug("Skipped review sync: reason=noPullRequestsFound, repoName={}", safeNameWithOwner);
            return 0;
        }

        log.info(
            "Completed review sync: repoName={}, reviewCount={}, prCount={}",
            safeNameWithOwner,
            totalSynced.get(),
            prCount.get()
        );
        return totalSynced.get();
    }

    /**
     * Synchronizes all reviews for a single pull request.
     * <p>
     * Note: When called from GitHubPullRequestSyncService, the first batch of reviews
     * (up to 10) has already been processed inline. This method will re-process them
     * (idempotent update) and fetch any remaining reviews. For better efficiency,
     * consider using {@link #syncRemainingReviews(Long, PullRequest, String)} with
     * a starting cursor.
     */
    public int syncForPullRequest(Long scopeId, PullRequest pullRequest) {
        return syncRemainingReviews(scopeId, pullRequest, null);
    }

    /**
     * Synchronizes remaining reviews for a pull request starting from a cursor.
     * <p>
     * This method is non-transactional: it fetches data from the GraphQL API and
     * delegates persistence to {@link #processReviewPageInTransaction} which runs
     * each page in a {@code REQUIRES_NEW} transaction. This ensures that a deadlock
     * on one page does not poison the entire sync, and retries start fresh transactions.
     *
     * @param scopeId  the scope ID for authentication
     * @param pullRequest  the pull request to sync reviews for
     * @param startCursor  the cursor to start from (null to fetch all reviews)
     * @return number of reviews synced
     */
    public int syncRemainingReviews(Long scopeId, PullRequest pullRequest, String startCursor) {
        if (pullRequest == null || pullRequest.getRepository() == null) {
            log.warn(
                "Skipped review sync: reason=prOrRepositoryNull, prId={}",
                pullRequest != null ? pullRequest.getId() : "null"
            );
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Skipped review sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;
        int pageCount = 0;
        int retryAttempt = 0;

        while (hasMore) {
            // Check for interrupt (e.g., during application shutdown)
            if (Thread.interrupted()) {
                log.info(
                    "Review sync interrupted (shutdown requested): repoName={}, prNumber={}, pageCount={}",
                    safeNameWithOwner,
                    pullRequest.getNumber(),
                    pageCount
                );
                Thread.currentThread().interrupt();
                break;
            }

            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for reviews: repoName={}, prNumber={}, limit={}",
                    safeNameWithOwner,
                    pullRequest.getNumber(),
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("number", pullRequest.getNumber())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(syncProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    // Check if this is a NOT_FOUND error (PR deleted from GitHub)
                    if (isNotFoundError(response, "repository.pullRequest")) {
                        log.debug(
                            "Skipped review sync: reason=prDeletedFromGitHub, repoName={}, prNumber={}",
                            safeNameWithOwner,
                            pullRequest.getNumber()
                        );
                        return totalSynced;
                    }
                    ClassificationResult classification = graphQlSyncHelper.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncHelper.handleGraphQlClassification(
                                classification,
                                retryAttempt,
                                MAX_RETRY_ATTEMPTS,
                                "review sync",
                                "prNumber",
                                pullRequest.getNumber(),
                                log
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for reviews: repoName={}, prNumber={}, errors={}",
                        safeNameWithOwner,
                        pullRequest.getNumber(),
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (
                        !graphQlSyncHelper.waitForRateLimitIfNeeded(
                            scopeId,
                            "review sync",
                            "prNumber",
                            pullRequest.getNumber(),
                            log
                        )
                    ) {
                        break;
                    }
                }

                GHPullRequestReviewConnection connection = response
                    .field("repository.pullRequest.reviews")
                    .toEntity(GHPullRequestReviewConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                // Fetch any remaining nested review comments before persistence
                for (var graphQlReview : connection.getNodes()) {
                    var commentsConnection = graphQlReview.getComments();
                    if (commentsConnection != null) {
                        var commentsPageInfo = commentsConnection.getPageInfo();
                        if (commentsPageInfo != null && Boolean.TRUE.equals(commentsPageInfo.getHasNextPage())) {
                            fetchAllRemainingComments(client, graphQlReview, commentsPageInfo.getEndCursor(), scopeId);
                        }
                    }
                }

                // Persist the page in a REQUIRES_NEW transaction with deadlock retry
                int pageSynced = persistReviewPageWithRetry(
                    connection.getNodes(),
                    pullRequest.getId(),
                    scopeId,
                    repository,
                    safeNameWithOwner,
                    pullRequest.getNumber()
                );
                totalSynced += pageSynced;

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                retryAttempt = 0;
            } catch (InstallationNotFoundException e) {
                log.warn("Installation not found for repository {}, skipping review sync", safeNameWithOwner);
                return totalSynced;
            } catch (FieldAccessException e) {
                // Check if this is a NOT_FOUND error (PR deleted from GitHub)
                if (isNotFoundError(e.getResponse(), "repository.pullRequest")) {
                    log.debug(
                        "Skipped review sync: reason=prDeletedFromGitHub, repoName={}, prNumber={}",
                        safeNameWithOwner,
                        pullRequest.getNumber()
                    );
                    return totalSynced;
                }
                log.error(
                    "Failed to sync reviews: repoName={}, prNumber={}",
                    safeNameWithOwner,
                    pullRequest.getNumber(),
                    e
                );
                break;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    !graphQlSyncHelper.handleGraphQlClassification(
                        classification,
                        retryAttempt,
                        MAX_RETRY_ATTEMPTS,
                        "review sync",
                        "prNumber",
                        pullRequest.getNumber(),
                        log
                    )
                ) {
                    break;
                }
                retryAttempt++;
            }
        }

        log.debug(
            "Completed review sync for pull request: repoName={}, prNumber={}, reviewCount={}",
            safeNameWithOwner,
            pullRequest.getNumber(),
            totalSynced
        );
        return totalSynced;
    }

    /**
     * Persists a page of reviews with transient failure retry. Each attempt runs in a fresh
     * {@code REQUIRES_NEW} transaction via self-proxy, so a deadlock on one attempt
     * does not poison subsequent retries.
     */
    private int persistReviewPageWithRetry(
        List<GHPullRequestReview> reviews,
        Long pullRequestId,
        Long scopeId,
        Repository repository,
        String safeNameWithOwner,
        int prNumber
    ) {
        for (int attempt = 0; attempt <= MAX_DEADLOCK_RETRIES; attempt++) {
            try {
                return self.processReviewPageInTransaction(reviews, pullRequestId, scopeId, repository);
            } catch (Exception e) {
                boolean retryable;
                String errorDetail;
                if (e instanceof org.springframework.transaction.UnexpectedRollbackException) {
                    retryable = true;
                    errorDetail = "Transaction rolled back (likely deadlock): " + e.getMessage();
                } else {
                    ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                    retryable = classification.category() == GitHubExceptionClassifier.Category.RETRYABLE;
                    errorDetail = classification.message();
                }

                if (retryable && attempt < MAX_DEADLOCK_RETRIES) {
                    log.warn(
                        "Transient failure during review page persistence (attempt {}/{}), retrying: repoName={}, prNumber={}, error={}",
                        attempt + 1,
                        MAX_DEADLOCK_RETRIES,
                        safeNameWithOwner,
                        prNumber,
                        errorDetail
                    );
                    try {
                        ExponentialBackoff.sleep(attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted during retry backoff for review page: prNumber={}", prNumber);
                        return 0;
                    }
                } else {
                    log.error(
                        "Failed to persist review page after {} attempts: repoName={}, prNumber={}, error={}",
                        attempt + 1,
                        safeNameWithOwner,
                        prNumber,
                        errorDetail,
                        e
                    );
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Processes a page of review nodes in a {@code REQUIRES_NEW} transaction.
     * <p>
     * Called via self-proxy to ensure the transaction annotation is honoured.
     * If a deadlock occurs, the transaction is rolled back independently without
     * poisoning any outer transaction.
     *
     * @param reviews       the review nodes from the GraphQL response
     * @param pullRequestId the database ID of the owning pull request
     * @param scopeId       the scope ID for authentication
     * @param repository    the repository entity for creating the processing context
     * @return number of reviews persisted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processReviewPageInTransaction(
        List<GHPullRequestReview> reviews,
        Long pullRequestId,
        Long scopeId,
        Repository repository
    ) {
        ProcessingContext context = ProcessingContext.forSync(scopeId, repository);
        int synced = 0;
        for (var graphQlReview : reviews) {
            GitHubReviewDTO dto = GitHubReviewDTO.fromPullRequestReview(graphQlReview);
            if (dto != null) {
                PullRequestReview entity = reviewProcessor.process(dto, pullRequestId, context);
                if (entity != null) {
                    synced++;
                }
            }
        }
        return synced;
    }

    /**
     * Processes a single inline review DTO during pull request sync.
     * <p>
     * This delegation method allows the PR sync service to process embedded reviews
     * without depending directly on the review processor.
     *
     * @param reviewDTO    the review DTO to process
     * @param pullRequestId the database ID of the owning pull request
     * @param context       the processing context for activity event creation
     * @return the persisted review entity, or null if processing was skipped
     */
    @Transactional
    public PullRequestReview processInlineReview(
        GitHubReviewDTO reviewDTO,
        Long pullRequestId,
        ProcessingContext context
    ) {
        return reviewProcessor.process(reviewDTO, pullRequestId, context);
    }

    /**
     * Fetches all remaining comments for a review when the initial query hit the pagination limit.
     * This method modifies the graphQlReview's comments list in place by adding all fetched comments.
     *
     * @param client       the GraphQL client
     * @param graphQlReview the review to fetch remaining comments for
     * @param startCursor  the cursor to start fetching from
     */
    private void fetchAllRemainingComments(
        HttpGraphQlClient client,
        GHPullRequestReview graphQlReview,
        String startCursor,
        Long scopeId
    ) {
        String reviewId = graphQlReview.getId();
        GHPullRequestReviewCommentConnection existingComments = graphQlReview.getComments();
        if (existingComments == null || existingComments.getNodes() == null) {
            return;
        }

        // Create a mutable list to collect all comments
        List<GHPullRequestReviewComment> allComments = new ArrayList<>(existingComments.getNodes());

        String cursor = startCursor;
        boolean hasMore = true;
        int fetchedPages = 0;
        int retryAttempt = 0;

        while (hasMore) {
            // Check for interrupt (e.g., during application shutdown)
            if (Thread.interrupted()) {
                log.info(
                    "Review comments fetch interrupted (shutdown requested): reviewId={}, fetchedPages={}",
                    reviewId,
                    fetchedPages
                );
                Thread.currentThread().interrupt();
                break;
            }

            fetchedPages++;
            if (fetchedPages > MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for review comments: reviewId={}, limit={}",
                    reviewId,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                ClientGraphQlResponse response = client
                    .documentName(REVIEW_COMMENTS_QUERY_DOCUMENT)
                    .variable("reviewId", reviewId)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(syncProperties.graphqlTimeout());

                if (response == null || !response.isValid()) {
                    ClassificationResult classification = graphQlSyncHelper.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncHelper.handleGraphQlClassification(
                                classification,
                                retryAttempt,
                                MAX_RETRY_ATTEMPTS,
                                "review comments fetch",
                                "reviewId",
                                reviewId,
                                log
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for review comments: reviewId={}, errors={}",
                        reviewId,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, response);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (
                        !graphQlSyncHelper.waitForRateLimitIfNeeded(
                            scopeId,
                            "review comments fetch",
                            "reviewId",
                            reviewId,
                            log
                        )
                    ) {
                        break;
                    }
                }

                GHPullRequestReviewCommentConnection commentsConnection = response
                    .field("node.comments")
                    .toEntity(GHPullRequestReviewCommentConnection.class);

                if (commentsConnection == null || commentsConnection.getNodes() == null) {
                    break;
                }

                allComments.addAll(commentsConnection.getNodes());

                GHPageInfo pageInfo = commentsConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                retryAttempt = 0;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    !graphQlSyncHelper.handleGraphQlClassification(
                        classification,
                        retryAttempt,
                        MAX_RETRY_ATTEMPTS,
                        "review comments fetch",
                        "reviewId",
                        reviewId,
                        log
                    )
                ) {
                    break;
                }
                retryAttempt++;
            }
        }

        // Update the review's comments with the complete list
        existingComments.setNodes(allComments);

        if (fetchedPages > 0) {
            log.debug(
                "Fetched additional review comments: reviewId={}, pageCount={}, totalComments={}",
                reviewId,
                fetchedPages,
                allComments.size()
            );
        }
    }
}

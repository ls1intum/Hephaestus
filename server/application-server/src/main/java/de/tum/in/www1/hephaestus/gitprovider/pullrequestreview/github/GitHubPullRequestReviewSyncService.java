package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlErrorUtils.isNotFoundError;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
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
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request reviews via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubPullRequestReviewProcessor.
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

    public GitHubPullRequestReviewSyncService(
        RepositoryRepository repositoryRepository,
        PullRequestRepository pullRequestRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestReviewProcessor reviewProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.reviewProcessor = reviewProcessor;
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
            log.warn("Skipped review sync: reason=repositoryNotFound, repoId={}", repositoryId);
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

        log.info("Completed review sync: repoName={}, reviewCount={}, prCount={}", safeNameWithOwner, totalSynced.get(), prCount.get());
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
    @Transactional
    public int syncForPullRequest(Long scopeId, PullRequest pullRequest) {
        return syncRemainingReviews(scopeId, pullRequest, null);
    }

    /**
     * Synchronizes remaining reviews for a pull request starting from a cursor.
     * <p>
     * This method is optimized for cases where initial reviews have already been
     * processed inline with the PR query. Pass the endCursor from the embedded
     * reviews to skip already-processed reviews.
     *
     * @param scopeId  the scope ID for authentication
     * @param pullRequest  the pull request to sync reviews for
     * @param startCursor  the cursor to start from (null to fetch all reviews)
     * @return number of reviews synced
     */
    @Transactional
    public int syncRemainingReviews(Long scopeId, PullRequest pullRequest, String startCursor) {
        if (pullRequest == null || pullRequest.getRepository() == null) {
            log.warn("Skipped review sync: reason=prOrRepositoryNull, prId={}", pullRequest != null ? pullRequest.getId() : "null");
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

        while (hasMore) {
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
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    // Check if this is a NOT_FOUND error (PR deleted from GitHub)
                    if (isNotFoundError(response, "repository.pullRequest")) {
                        log.debug(
                            "Skipped review sync: reason=prDeletedFromGitHub, repoName={}, prNumber={}",
                            safeNameWithOwner,
                            pullRequest.getNumber()
                        );
                        return 0;
                    }
                    log.warn(
                        "Invalid GraphQL response for reviews: repoName={}, prNumber={}, errors={}",
                        safeNameWithOwner,
                        pullRequest.getNumber(),
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                GHPullRequestReviewConnection connection = response
                    .field("repository.pullRequest.reviews")
                    .toEntity(GHPullRequestReviewConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlReview : connection.getNodes()) {
                    // Handle nested pagination for review comments
                    var commentsConnection = graphQlReview.getComments();
                    if (commentsConnection != null) {
                        var commentsPageInfo = commentsConnection.getPageInfo();
                        if (commentsPageInfo != null && Boolean.TRUE.equals(commentsPageInfo.getHasNextPage())) {
                            // Fetch all remaining comments using pagination
                            fetchAllRemainingComments(client, graphQlReview, commentsPageInfo.getEndCursor());
                        }
                    }

                    GitHubReviewDTO dto = GitHubReviewDTO.fromPullRequestReview(graphQlReview);
                    if (dto != null) {
                        PullRequestReview entity = reviewProcessor.process(dto, pullRequest.getId());
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                GHPageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (FieldAccessException e) {
                // Check if this is a NOT_FOUND error (PR deleted from GitHub)
                if (isNotFoundError(e.getResponse(), "repository.pullRequest")) {
                    log.debug(
                        "Skipped review sync: reason=prDeletedFromGitHub, repoName={}, prNumber={}",
                        safeNameWithOwner,
                        pullRequest.getNumber()
                    );
                    return 0;
                }
                log.error(
                    "Failed to sync reviews: repoName={}, prNumber={}",
                    safeNameWithOwner,
                    pullRequest.getNumber(),
                    e
                );
                break;
            } catch (Exception e) {
                log.error(
                    "Failed to sync reviews: repoName={}, prNumber={}",
                    safeNameWithOwner,
                    pullRequest.getNumber(),
                    e
                );
                break;
            }
        }

        log.debug("Synced reviews for pull request: repoName={}, prNumber={}, reviewCount={}", safeNameWithOwner, pullRequest.getNumber(), totalSynced);
        return totalSynced;
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
        String startCursor
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

        while (hasMore) {
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
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn(
                        "Invalid GraphQL response for review comments: reviewId={}, errors={}",
                        reviewId,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
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
            } catch (Exception e) {
                log.error("Failed to fetch additional review comments: reviewId={}", reviewId, e);
                break;
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

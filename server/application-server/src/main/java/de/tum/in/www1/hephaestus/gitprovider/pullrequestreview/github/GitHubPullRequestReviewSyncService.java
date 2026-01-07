package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewConnection;
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
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync reviews for
     * @return number of reviews synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping review sync", repositoryId);
            return 0;
        }

        AtomicInteger totalSynced = new AtomicInteger(0);
        AtomicInteger prCount = new AtomicInteger(0);

        try (Stream<PullRequest> prStream = pullRequestRepository.streamAllByRepository_Id(repositoryId)) {
            prStream.forEach(pullRequest -> {
                totalSynced.addAndGet(syncForPullRequest(workspaceId, pullRequest));
                prCount.incrementAndGet();
            });
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        if (prCount.get() == 0) {
            log.info("No pull requests found for {}, skipping review sync", safeNameWithOwner);
            return 0;
        }

        log.info("Synced {} reviews for {} PRs in {}", totalSynced.get(), prCount.get(), safeNameWithOwner);
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
    public int syncForPullRequest(Long workspaceId, PullRequest pullRequest) {
        return syncRemainingReviews(workspaceId, pullRequest, null);
    }

    /**
     * Synchronizes remaining reviews for a pull request starting from a cursor.
     * <p>
     * This method is optimized for cases where initial reviews have already been
     * processed inline with the PR query. Pass the endCursor from the embedded
     * reviews to skip already-processed reviews.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param pullRequest  the pull request to sync reviews for
     * @param startCursor  the cursor to start from (null to fetch all reviews)
     * @return number of reviews synced
     */
    @Transactional
    public int syncRemainingReviews(Long workspaceId, PullRequest pullRequest, String startCursor) {
        if (pullRequest == null || pullRequest.getRepository() == null) {
            log.warn("Pull request or repository is null, skipping review sync");
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        int totalSynced = 0;
        String cursor = startCursor;
        boolean hasMore = true;

        while (hasMore) {
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
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
                }

                PullRequestReviewConnection connection = response
                    .field("repository.pullRequest.reviews")
                    .toEntity(PullRequestReviewConnection.class);

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

                PageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error(
                    "Error syncing reviews for PR #{} in {}: {}",
                    pullRequest.getNumber(),
                    safeNameWithOwner,
                    e.getMessage(),
                    e
                );
                break;
            }
        }

        log.debug("Synced {} reviews for PR #{} in {}", totalSynced, pullRequest.getNumber(), safeNameWithOwner);
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
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReview graphQlReview,
        String startCursor
    ) {
        String reviewId = graphQlReview.getId();
        PullRequestReviewCommentConnection existingComments = graphQlReview.getComments();
        if (existingComments == null || existingComments.getNodes() == null) {
            return;
        }

        // Create a mutable list to collect all comments
        List<de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewComment> allComments =
            new ArrayList<>(existingComments.getNodes());

        String cursor = startCursor;
        boolean hasMore = true;
        int fetchedPages = 0;

        while (hasMore) {
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
                        "Invalid GraphQL response fetching comments for review {}: {}",
                        reviewId,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                PullRequestReviewCommentConnection commentsConnection = response
                    .field("node.comments")
                    .toEntity(PullRequestReviewCommentConnection.class);

                if (commentsConnection == null || commentsConnection.getNodes() == null) {
                    break;
                }

                allComments.addAll(commentsConnection.getNodes());
                fetchedPages++;

                PageInfo pageInfo = commentsConnection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Error fetching additional comments for review {}: {}", reviewId, e.getMessage(), e);
                break;
            }
        }

        // Update the review's comments with the complete list
        existingComments.setNodes(allComments);

        if (fetchedPages > 0) {
            log.debug(
                "Fetched {} additional pages of comments for review {} (total: {} comments)",
                fetchedPages,
                reviewId,
                allComments.size()
            );
        }
    }
}

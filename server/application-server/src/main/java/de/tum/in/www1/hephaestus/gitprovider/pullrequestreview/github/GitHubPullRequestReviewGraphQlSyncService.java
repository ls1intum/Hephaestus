package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Actor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewState;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request reviews via GraphQL API.
 * <p>
 * This service fetches pull request reviews via GraphQL and uses
 * GitHubPullRequestReviewProcessor for persistence. It supports syncing
 * reviews for a single PR or all PRs in a repository.
 */
@Service
public class GitHubPullRequestReviewGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewGraphQlSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 50;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
    private static final String GET_PR_REVIEWS_DOCUMENT = "GetPullRequestReviews";

    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestReviewProcessor reviewProcessor;

    public GitHubPullRequestReviewGraphQlSyncService(
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
     * Synchronizes all reviews for all pull requests in a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync reviews for
     * @return number of reviews synced
     */
    @Transactional
    public int syncReviewsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not found, cannot sync reviews", repositoryId);
            return 0;
        }

        List<PullRequest> pullRequests = pullRequestRepository.findAllByRepository_Id(repositoryId);
        if (pullRequests.isEmpty()) {
            logger.info(
                "No pull requests found for repository {}, skipping review sync",
                repository.getNameWithOwner()
            );
            return 0;
        }

        int totalSynced = 0;
        for (PullRequest pullRequest : pullRequests) {
            int synced = syncReviewsForPullRequest(workspaceId, pullRequest);
            totalSynced += synced;
        }

        logger.info(
            "Synced {} reviews for {} pull requests in repository {}",
            totalSynced,
            pullRequests.size(),
            repository.getNameWithOwner()
        );
        return totalSynced;
    }

    /**
     * Synchronizes all reviews for a single pull request using GraphQL.
     *
     * @param workspaceId the workspace ID for authentication
     * @param pullRequest the pull request to sync reviews for
     * @return number of reviews synced
     */
    @Transactional
    public int syncReviewsForPullRequest(Long workspaceId, PullRequest pullRequest) {
        if (pullRequest == null) {
            logger.warn("Pull request is null, cannot sync reviews");
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        if (repository == null) {
            logger.warn("Pull request {} has no repository, cannot sync reviews", pullRequest.getId());
            return 0;
        }

        String[] parts = repository.getNameWithOwner().split("/");
        if (parts.length != 2) {
            logger.warn("Invalid repository nameWithOwner: {}", repository.getNameWithOwner());
            return 0;
        }
        String owner = parts[0];
        String name = parts[1];

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                PullRequestReviewConnection response = client
                    .documentName(GET_PR_REVIEWS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", pullRequest.getNumber())
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.pullRequest.reviews")
                    .toEntity(PullRequestReviewConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlReview : response.getNodes()) {
                    GitHubReviewDTO dto = convertToDTO(graphQlReview);
                    if (dto != null) {
                        PullRequestReview review = reviewProcessor.process(dto, pullRequest.getId());
                        if (review != null) {
                            totalSynced++;
                        }
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            logger.debug(
                "Synced {} reviews for PR #{} in repository {}",
                totalSynced,
                pullRequest.getNumber(),
                repository.getNameWithOwner()
            );
            return totalSynced;
        } catch (Exception e) {
            logger.error(
                "Error syncing reviews for PR #{} in repository {}: {}",
                pullRequest.getNumber(),
                repository.getNameWithOwner(),
                e.getMessage(),
                e
            );
            return 0;
        }
    }

    /**
     * Converts a GraphQL PullRequestReview to a GitHubReviewDTO.
     *
     * @param graphQlReview the GraphQL pull request review
     * @return the DTO for processing, or null if databaseId is missing
     */
    private GitHubReviewDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReview graphQlReview
    ) {
        if (graphQlReview == null) {
            return null;
        }

        // databaseId is required for persistence
        Integer databaseId = graphQlReview.getDatabaseId();
        if (databaseId == null) {
            logger.warn("Review has no databaseId, skipping: nodeId={}", graphQlReview.getId());
            return null;
        }

        // Convert author
        GitHubUserDTO author = null;
        Actor graphQlAuthor = graphQlReview.getAuthor();
        if (graphQlAuthor instanceof User graphQlUser) {
            author = new GitHubUserDTO(
                null, // id (node_id)
                graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null, // databaseId
                graphQlUser.getLogin(), // login
                graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null, // avatarUrl
                null, // htmlUrl
                null, // name
                null // email
            );
        }

        // Convert state
        String state = convertState(graphQlReview.getState());

        return new GitHubReviewDTO(
            databaseId.longValue(), // id
            graphQlReview.getId(), // nodeId
            graphQlReview.getBody(), // body
            state, // state
            graphQlReview.getUrl() != null ? graphQlReview.getUrl().toString() : null, // htmlUrl
            author, // author
            graphQlReview.getSubmittedAt() != null ? graphQlReview.getSubmittedAt().toInstant() : null, // submittedAt
            null // commitId - not directly available in the GraphQL query, would need to fetch commit.oid
        );
    }

    /**
     * Converts a GraphQL PullRequestReviewState to its string representation.
     *
     * @param state the GraphQL review state
     * @return the string representation
     */
    private String convertState(PullRequestReviewState state) {
        if (state == null) {
            return null;
        }
        return state.name();
    }
}

package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlParsingUtils.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull request reviews via GraphQL API.
 * <p>
 * This service fetches pull request reviews via GraphQL and uses
 * GitHubPullRequestReviewProcessor for persistence. Uses Map-based parsing
 * to handle GitHub's polymorphic Actor interface.
 */
@Service
public class GitHubPullRequestReviewGraphQlSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewGraphQlSyncService.class);
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
    @SuppressWarnings("unchecked")
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

        String[] parts = parseRepositoryName(repository.getNameWithOwner());
        if (parts == null) {
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
                ClientGraphQlResponse response = client
                    .documentName(GET_PR_REVIEWS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("number", pullRequest.getNumber())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(DEFAULT_TIMEOUT);

                if (response == null || !response.isValid()) {
                    logger.warn(
                        "Invalid GraphQL response for PR reviews: {}",
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                Map<String, Object> reviewsData = response.field("repository.pullRequest.reviews").toEntity(Map.class);
                if (reviewsData == null) {
                    break;
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) reviewsData.get("nodes");
                if (nodes == null || nodes.isEmpty()) {
                    break;
                }

                for (Map<String, Object> reviewData : nodes) {
                    GitHubReviewDTO dto = convertMapToDTO(reviewData);
                    if (dto != null) {
                        PullRequestReview review = reviewProcessor.process(dto, pullRequest.getId());
                        if (review != null) {
                            totalSynced++;
                        }
                    }
                }

                Map<String, Object> pageInfo = (Map<String, Object>) reviewsData.get("pageInfo");
                hasNextPage = pageInfo != null && parseBoolean(pageInfo.get("hasNextPage"));
                cursor = pageInfo != null ? getString(pageInfo, "endCursor") : null;
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

    @SuppressWarnings("unchecked")
    private GitHubReviewDTO convertMapToDTO(Map<String, Object> reviewData) {
        // Parse author
        GitHubUserDTO author = parseUser((Map<String, Object>) reviewData.get("author"));

        // Parse state
        String state = getString(reviewData, "state");

        return new GitHubReviewDTO(
            parseLong(reviewData.get("databaseId")), // id
            getString(reviewData, "id"), // nodeId
            getString(reviewData, "body"), // body
            convertReviewState(state), // state
            getString(reviewData, "url"), // htmlUrl
            author, // user
            parseInstant(reviewData.get("submittedAt")), // submittedAt
            getString(reviewData, "commitId") // commitId
        );
    }

    private String convertReviewState(String state) {
        if (state == null) {
            return "PENDING";
        }
        // GraphQL returns the state in uppercase, keep as-is
        return state;
    }
}

package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Duration;
import java.util.List;
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
    private static final int PAGE_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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

        List<PullRequest> pullRequests = pullRequestRepository.findAllByRepository_Id(repositoryId);
        if (pullRequests.isEmpty()) {
            log.info("No pull requests found for {}, skipping review sync", repository.getNameWithOwner());
            return 0;
        }

        int totalSynced = 0;
        for (PullRequest pullRequest : pullRequests) {
            totalSynced += syncForPullRequest(workspaceId, pullRequest);
        }

        log.info("Synced {} reviews for {} PRs in {}", totalSynced, pullRequests.size(), repository.getNameWithOwner());
        return totalSynced;
    }

    /**
     * Synchronizes all reviews for a single pull request.
     */
    @Transactional
    public int syncForPullRequest(Long workspaceId, PullRequest pullRequest) {
        if (pullRequest == null || pullRequest.getRepository() == null) {
            log.warn("Pull request or repository is null, skipping review sync");
            return 0;
        }

        Repository repository = pullRequest.getRepository();
        String[] ownerAndName = parseOwnerAndName(repository.getNameWithOwner());
        if (ownerAndName == null) {
            log.warn("Invalid repository name format: {}", repository.getNameWithOwner());
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName[0])
                    .variable("name", ownerAndName[1])
                    .variable("number", pullRequest.getNumber())
                    .variable("first", PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(TIMEOUT);

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
                    repository.getNameWithOwner(),
                    e.getMessage(),
                    e
                );
                break;
            }
        }

        log.debug(
            "Synced {} reviews for PR #{} in {}",
            totalSynced,
            pullRequest.getNumber(),
            repository.getNameWithOwner()
        );
        return totalSynced;
    }

    private String[] parseOwnerAndName(String nameWithOwner) {
        if (nameWithOwner == null || !nameWithOwner.contains("/")) {
            return null;
        }
        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return parts;
    }
}

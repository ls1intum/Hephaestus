package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub pull requests via GraphQL API.
 * <p>
 * This service fetches PRs using typed GraphQL models and delegates persistence
 * to GitHubPullRequestProcessor.
 */
@Service
public class GitHubPullRequestSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryPullRequests";
    private static final int PAGE_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubPullRequestProcessor pullRequestProcessor;

    public GitHubPullRequestSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubPullRequestProcessor pullRequestProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.pullRequestProcessor = pullRequestProcessor;
    }

    /**
     * Synchronizes all pull requests for a repository.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync pull requests for
     * @return number of pull requests synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping pull request sync", repositoryId);
            return 0;
        }

        String[] ownerAndName = parseOwnerAndName(repository.getNameWithOwner());
        if (ownerAndName == null) {
            log.warn("Invalid repository name format: {}", repository.getNameWithOwner());
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName[0])
                    .variable("name", ownerAndName[1])
                    .variable("first", PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
                }

                PullRequestConnection connection = response
                    .field("repository.pullRequests")
                    .toEntity(PullRequestConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlPullRequest : connection.getNodes()) {
                    GitHubPullRequestDTO dto = GitHubPullRequestDTO.fromPullRequest(graphQlPullRequest);
                    if (dto != null) {
                        PullRequest entity = pullRequestProcessor.process(dto, context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                PageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Error syncing pull requests for {}: {}", repository.getNameWithOwner(), e.getMessage(), e);
                break;
            }
        }

        log.info("Synced {} pull requests for {}", totalSynced, repository.getNameWithOwner());
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

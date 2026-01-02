package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.github.dto.GitHubCommitDTO;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Commit;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.CommitHistoryConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub commits via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubCommitProcessor.
 */
@Service
public class GitHubCommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryCommits";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubCommitProcessor commitProcessor;

    public GitHubCommitSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubCommitProcessor commitProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.commitProcessor = commitProcessor;
    }

    /**
     * Synchronizes commits for a repository's default branch.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync commits for
     * @return number of commits synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        return syncForRepository(workspaceId, repositoryId, "refs/heads/main", null);
    }

    /**
     * Synchronizes commits for a repository on a specific branch.
     *
     * @param workspaceId   the workspace ID for authentication
     * @param repositoryId  the repository ID to sync commits for
     * @param qualifiedName the qualified branch name (e.g., "refs/heads/main")
     * @param since         only fetch commits after this date (optional)
     * @return number of commits synced
     */
    @Transactional
    public int syncForRepository(
        Long workspaceId,
        Long repositoryId,
        String qualifiedName,
        @Nullable OffsetDateTime since
    ) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping commit sync", repositoryId);
            return 0;
        }

        String nameWithOwner = repository.getNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(nameWithOwner);
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        RepositoryOwnerAndName ownerAndName = parsedName.get();

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        ProcessingContext context = ProcessingContext.forSync(workspaceId, repository);

        int totalSynced = 0;
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                var request = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("qualifiedName", qualifiedName)
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor);

                if (since != null) {
                    request = request.variable("since", since.toString());
                }

                ClientGraphQlResponse response = request.execute().block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
                }

                // Navigate through the nested structure: repository.ref.target.history
                CommitHistoryConnection history = response
                    .field("repository.ref.target.history")
                    .toEntity(CommitHistoryConnection.class);

                if (history == null || history.getNodes() == null || history.getNodes().isEmpty()) {
                    break;
                }

                for (Commit graphQlCommit : history.getNodes()) {
                    GitHubCommitDTO dto = GitHubCommitDTO.fromCommit(graphQlCommit);
                    if (dto != null) {
                        GitCommit entity = commitProcessor.process(dto, context, qualifiedName, null);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                PageInfo pageInfo = history.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Error syncing commits for {}: {}", safeNameWithOwner, e.getMessage(), e);
                break;
            }
        }

        log.info("Synced {} commits for {} on {}", totalSynced, safeNameWithOwner, qualifiedName);
        return totalSynced;
    }
}

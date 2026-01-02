package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.DEFAULT_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.DiscussionConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub discussions via GraphQL API.
 * <p>
 * Uses typed GraphQL models for type-safe deserialization and delegates
 * persistence to GitHubDiscussionProcessor.
 */
@Service
public class GitHubDiscussionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiscussionSyncService.class);
    private static final String QUERY_DOCUMENT = "GetRepositoryDiscussions";

    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubDiscussionProcessor discussionProcessor;

    public GitHubDiscussionSyncService(
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubDiscussionProcessor discussionProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.discussionProcessor = discussionProcessor;
    }

    /**
     * Synchronizes all discussions for a repository.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync discussions for
     * @return number of discussions synced
     */
    @Transactional
    public int syncForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, skipping discussion sync", repositoryId);
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
                ClientGraphQlResponse response = client
                    .documentName(QUERY_DOCUMENT)
                    .variable("owner", ownerAndName.owner())
                    .variable("name", ownerAndName.name())
                    .variable("first", DEFAULT_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || !response.isValid()) {
                    log.warn("Invalid GraphQL response: {}", response != null ? response.getErrors() : "null");
                    break;
                }

                DiscussionConnection connection = response
                    .field("repository.discussions")
                    .toEntity(DiscussionConnection.class);

                if (connection == null || connection.getNodes() == null || connection.getNodes().isEmpty()) {
                    break;
                }

                for (var graphQlDiscussion : connection.getNodes()) {
                    GitHubDiscussionDTO dto = GitHubDiscussionDTO.fromDiscussion(graphQlDiscussion);
                    if (dto != null) {
                        Discussion entity = discussionProcessor.process(dto, context);
                        if (entity != null) {
                            totalSynced++;
                        }
                    }
                }

                PageInfo pageInfo = connection.getPageInfo();
                hasMore = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            } catch (Exception e) {
                log.error("Error syncing discussions for {}: {}", safeNameWithOwner, e.getMessage(), e);
                break;
            }
        }

        log.info("Synced {} discussions for {}", totalSynced, safeNameWithOwner);
        return totalSynced;
    }
}

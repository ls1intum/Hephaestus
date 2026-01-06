package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.RepositoryCollaboratorConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.RepositoryCollaboratorEdge;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub repository collaborators via GraphQL API.
 * <p>
 * This service fetches collaborators via GraphQL and uses {@link GitHubUserProcessor}
 * to persist users, ensuring a single source of truth for user processing logic.
 */
@Service
public class GitHubCollaboratorSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCollaboratorSyncService.class);
    private static final String GET_COLLABORATORS_DOCUMENT = "GetRepositoryCollaborators";

    private final RepositoryRepository repositoryRepository;
    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubUserProcessor userProcessor;

    public GitHubCollaboratorSyncService(
        RepositoryRepository repositoryRepository,
        RepositoryCollaboratorRepository collaboratorRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubUserProcessor userProcessor
    ) {
        this.repositoryRepository = repositoryRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.userProcessor = userProcessor;
    }

    /**
     * Synchronizes all collaborators for a repository using GraphQL.
     *
     * @param workspaceId  the workspace ID for authentication
     * @param repositoryId the repository ID to sync collaborators for
     * @return number of collaborators synced
     */
    @Transactional
    public int syncCollaboratorsForRepository(Long workspaceId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.warn("Repository {} not found, cannot sync collaborators", repositoryId);
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            Set<Long> syncedUserIds = new HashSet<>();
            int pageCount = 0;

            while (hasNextPage) {
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit ({}) for repository {}, stopping",
                        MAX_PAGINATION_PAGES,
                        safeNameWithOwner
                    );
                    break;
                }
                pageCount++;

                RepositoryCollaboratorConnection response = client
                    .documentName(GET_COLLABORATORS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.collaborators")
                    .toEntity(RepositoryCollaboratorConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getEdges() == null) {
                    break;
                }

                for (RepositoryCollaboratorEdge edge : response.getEdges()) {
                    var graphQlUser = edge.getNode();
                    if (graphQlUser == null) {
                        continue;
                    }

                    // Convert GraphQL User to DTO and upsert
                    GitHubUserDTO userDTO = GitHubUserDTO.fromUser(graphQlUser);
                    User user = userProcessor.findOrCreate(userDTO);
                    if (user == null) {
                        continue;
                    }

                    // Get permission from edge
                    RepositoryCollaborator.Permission permission = parsePermission(edge.getPermission());

                    // Upsert collaborator relationship
                    upsertCollaborator(repository, user, permission);
                    syncedUserIds.add(user.getId());
                    totalSynced++;
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            // Remove stale collaborators (collaborators in DB that no longer exist on GitHub)
            int removedCount = removeStaleCollaborators(repositoryId, syncedUserIds);

            log.info(
                "Synced {} collaborators for repository {} (removed {} stale)",
                totalSynced,
                safeNameWithOwner,
                removedCount
            );
            return totalSynced;
        } catch (Exception e) {
            log.error("Error syncing collaborators for repository {}: {}", safeNameWithOwner, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Upserts a collaborator relationship between a repository and a user.
     *
     * @param repository the repository
     * @param user       the user
     * @param permission the permission level
     */
    private void upsertCollaborator(Repository repository, User user, RepositoryCollaborator.Permission permission) {
        Optional<RepositoryCollaborator> existing = collaboratorRepository.findByRepositoryIdAndUserId(
            repository.getId(),
            user.getId()
        );

        if (existing.isPresent()) {
            // Update permission if changed
            RepositoryCollaborator collaborator = existing.get();
            collaborator.updatePermission(permission);
            collaboratorRepository.save(collaborator);
        } else {
            // Create new collaborator
            RepositoryCollaborator collaborator = new RepositoryCollaborator(repository, user, permission);
            collaboratorRepository.save(collaborator);
        }
    }

    /**
     * Parses a GraphQL RepositoryPermission to the entity Permission enum.
     *
     * @param permission the GraphQL permission enum
     * @return the entity Permission enum
     */
    private RepositoryCollaborator.Permission parsePermission(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.RepositoryPermission permission
    ) {
        if (permission == null) {
            return RepositoryCollaborator.Permission.UNKNOWN;
        }
        return RepositoryCollaborator.Permission.fromGitHubValue(permission.name());
    }

    /**
     * Removes collaborators from the local database that no longer exist on GitHub.
     *
     * @param repositoryId   the repository ID
     * @param syncedUserIds  the set of user IDs that were synced from GitHub
     * @return number of stale collaborators removed
     */
    private int removeStaleCollaborators(Long repositoryId, Set<Long> syncedUserIds) {
        List<RepositoryCollaborator> existingCollaborators = collaboratorRepository.findByRepository_Id(repositoryId);
        int removedCount = 0;

        for (RepositoryCollaborator existing : existingCollaborators) {
            // If user ID was not in the sync results, it's stale
            if (!syncedUserIds.contains(existing.getUser().getId())) {
                collaboratorRepository.delete(existing);
                removedCount++;
                log.debug(
                    "Removed stale collaborator: {} from repository {}",
                    existing.getUser().getLogin(),
                    repositoryId
                );
            }
        }

        return removedCount;
    }
}

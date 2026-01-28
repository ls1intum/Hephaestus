package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryCollaboratorConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryCollaboratorEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryPermission;
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
import org.springframework.graphql.client.ClientGraphQlResponse;
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
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubCollaboratorSyncService(
        RepositoryRepository repositoryRepository,
        RepositoryCollaboratorRepository collaboratorRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubUserProcessor userProcessor,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.userProcessor = userProcessor;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Synchronizes all collaborators for a repository using GraphQL.
     *
     * @param scopeId  the scope ID for authentication
     * @param repositoryId the repository ID to sync collaborators for
     * @return number of collaborators synced
     */
    @Transactional
    public int syncCollaboratorsForRepository(Long scopeId, Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId).orElse(null);
        if (repository == null) {
            log.debug("Skipped collaborator sync: reason=repositoryNotFound, repoId={}", repositoryId);
            return 0;
        }

        String safeNameWithOwner = sanitizeForLog(repository.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Skipped collaborator sync: reason=invalidRepoNameFormat, repoName={}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        try {
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            Set<Long> syncedUserIds = new HashSet<>();
            int pageCount = 0;
            boolean syncCompletedNormally = false;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for collaborators: repoName={}, limit={}",
                        safeNameWithOwner,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                ClientGraphQlResponse graphQlResponse = client
                    .documentName(GET_COLLABORATORS_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(syncProperties.graphqlTimeout());

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    log.warn(
                        "Received invalid GraphQL response: repoName={}, errors={}",
                        safeNameWithOwner,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    log.warn("Aborting collaborator sync due to critical rate limit: repoName={}", safeNameWithOwner);
                    break;
                }

                GHRepositoryCollaboratorConnection response = graphQlResponse
                    .field("repository.collaborators")
                    .toEntity(GHRepositoryCollaboratorConnection.class);

                if (response == null || response.getEdges() == null) {
                    break;
                }

                for (GHRepositoryCollaboratorEdge edge : response.getEdges()) {
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

            // Mark sync as completed normally if we exhausted all pages
            syncCompletedNormally = !hasNextPage;

            // CRITICAL: Only remove stale collaborators if sync completed fully.
            // If sync was aborted (rate limit, error, pagination limit), we don't have
            // the complete list and would incorrectly delete valid collaborators.
            int removedCount = 0;
            if (syncCompletedNormally) {
                removedCount = removeStaleCollaborators(repositoryId, syncedUserIds);
            } else {
                log.warn(
                    "Skipped stale collaborator removal: reason=incompleteSync, repoName={}, pagesProcessed={}",
                    safeNameWithOwner,
                    pageCount
                );
            }

            log.info(
                "Completed collaborator sync: repoName={}, collaboratorCount={}, removedCount={}, complete={}",
                safeNameWithOwner,
                totalSynced,
                removedCount,
                syncCompletedNormally
            );
            return totalSynced;
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case RATE_LIMITED -> log.warn(
                    "Rate limited during collaborator sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case NOT_FOUND -> log.warn(
                    "Resource not found during collaborator sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                case AUTH_ERROR -> {
                    log.error(
                        "Authentication error during collaborator sync: repoName={}, scopeId={}, message={}",
                        safeNameWithOwner,
                        scopeId,
                        classification.message()
                    );
                    throw e;
                }
                case RETRYABLE -> log.warn(
                    "Retryable error during collaborator sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message()
                );
                default -> log.error(
                    "Unexpected error during collaborator sync: repoName={}, scopeId={}, message={}",
                    safeNameWithOwner,
                    scopeId,
                    classification.message(),
                    e
                );
            }
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
     * Parses a GraphQL GHRepositoryPermission to the entity Permission enum.
     *
     * @param permission the GraphQL permission enum
     * @return the entity Permission enum
     */
    private RepositoryCollaborator.Permission parsePermission(GHRepositoryPermission permission) {
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
                    "Removed stale collaborator: repoId={}, userId={}, userLogin={}",
                    repositoryId,
                    existing.getUser().getId(),
                    sanitizeForLog(existing.getUser().getLogin())
                );
            }
        }

        return removedCount;
    }
}

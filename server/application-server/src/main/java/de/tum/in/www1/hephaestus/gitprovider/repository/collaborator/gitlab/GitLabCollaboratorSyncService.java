package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.adaptPageSize;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlResponseHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.gitlab.GitLabUserService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronizes GitLab project members as repository collaborators.
 * <p>
 * Uses the GitLab GraphQL {@code project.projectMembers} connection to
 * fetch all members with their access levels, then maps them to
 * {@link RepositoryCollaborator} entities.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabCollaboratorSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCollaboratorSyncService.class);
    private static final String GET_PROJECT_MEMBERS_DOCUMENT = "GetProjectMembers";
    private static final int LARGE_PAGE_SIZE = 100;

    private final RepositoryRepository repositoryRepository;
    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;
    private final GitLabUserService userService;
    private final GitLabProperties gitLabProperties;

    public GitLabCollaboratorSyncService(
        RepositoryRepository repositoryRepository,
        RepositoryCollaboratorRepository collaboratorRepository,
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGraphQlResponseHandler responseHandler,
        GitLabUserService userService,
        GitLabProperties gitLabProperties
    ) {
        this.repositoryRepository = repositoryRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
        this.userService = userService;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Syncs all collaborators for a repository from GitLab.
     *
     * @param scopeId      the workspace scope ID
     * @param repository   the repository to sync collaborators for
     * @return sync result
     */
    @Transactional
    public SyncResult syncCollaboratorsForRepository(Long scopeId, Repository repository) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);

        int totalSynced = 0;
        Set<Long> syncedUserIds = new HashSet<>();
        String cursor = null;
        String previousCursor = null;
        int page = 0;
        boolean errorAborted = false;

        try {
            do {
                if (page >= MAX_PAGINATION_PAGES) break;

                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = adaptPageSize(LARGE_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
                ClientGraphQlResponse response = client
                    .documentName(GET_PROJECT_MEMBERS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                var handleResult = responseHandler.handle(response, "collaborators for " + safeProjectPath, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(
                        new GitLabSyncException("Invalid response for project members")
                    );
                    errorAborted = true;
                    break;
                }
                graphQlClientProvider.recordSuccess();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) (List<?>) response
                    .field("project.projectMembers.nodes")
                    .toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) break;

                Long providerId = repository.getProvider() != null ? repository.getProvider().getId() : null;

                for (Map<String, Object> node : nodes) {
                    processCollaboratorNode(node, repository, providerId, syncedUserIds);
                    totalSynced++;
                }

                GitLabPageInfo pageInfo = response
                    .field("project.projectMembers.pageInfo")
                    .toEntity(GitLabPageInfo.class);
                cursor = pageInfo != null ? pageInfo.endCursor() : null;
                if (
                    responseHandler.isPaginationLoop(
                        cursor,
                        previousCursor,
                        "collaborators for " + safeProjectPath,
                        log
                    )
                ) {
                    errorAborted = true;
                    break;
                }
                previousCursor = cursor;
                page++;
                if (pageInfo == null || !pageInfo.hasNextPage()) break;
            } while (true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Collaborator sync interrupted: projectPath={}", safeProjectPath);
            return SyncResult.abortedRateLimit(totalSynced);
        } catch (Exception e) {
            log.warn("Collaborator sync failed: projectPath={}", safeProjectPath, e);
            errorAborted = true;
        }

        boolean syncComplete = !errorAborted && page < MAX_PAGINATION_PAGES;

        // Stale removal only when sync completed normally
        if (syncComplete) {
            removeStaleCollaborators(repository.getId(), syncedUserIds);
        }

        if (errorAborted) return SyncResult.abortedError(totalSynced);
        return SyncResult.completed(totalSynced);
    }

    @SuppressWarnings("unchecked")
    private void processCollaboratorNode(
        Map<String, Object> node,
        Repository repository,
        @Nullable Long providerId,
        Set<Long> syncedUserIds
    ) {
        Map<String, Object> userMap = (Map<String, Object>) node.get("user");
        Map<String, Object> accessLevel = (Map<String, Object>) node.get("accessLevel");

        if (userMap == null) return;

        String globalId = (String) userMap.get("id");
        String username = (String) userMap.get("username");
        String name = (String) userMap.get("name");
        String avatarUrl = (String) userMap.get("avatarUrl");
        String webUrl = (String) userMap.get("webUrl");

        if (globalId == null || username == null) return;

        User user = userService.findOrCreateUser(globalId, username, name, avatarUrl, webUrl, providerId);
        if (user == null) return;

        syncedUserIds.add(user.getId());

        // Map access level
        RepositoryCollaborator.Permission permission = RepositoryCollaborator.Permission.UNKNOWN;
        if (accessLevel != null) {
            Object intValue = accessLevel.get("integerValue");
            if (intValue instanceof Number num) {
                permission = mapGitLabAccessLevel(num.intValue());
            }
        }

        // Upsert collaborator
        RepositoryCollaborator collab = collaboratorRepository
            .findByRepositoryIdAndUserId(repository.getId(), user.getId())
            .orElse(null);

        if (collab == null) {
            collab = new RepositoryCollaborator(repository, user, permission);
        } else {
            collab.updatePermission(permission);
        }

        collaboratorRepository.save(collab);
    }

    private void removeStaleCollaborators(Long repositoryId, Set<Long> syncedUserIds) {
        List<RepositoryCollaborator> existing = collaboratorRepository.findByRepository_Id(repositoryId);
        int removed = 0;
        for (RepositoryCollaborator collab : existing) {
            if (!syncedUserIds.contains(collab.getUser().getId())) {
                collaboratorRepository.delete(collab);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Removed stale collaborators: repoId={}, count={}", repositoryId, removed);
        }
    }

    /**
     * Maps GitLab integer access levels to RepositoryCollaborator.Permission.
     * <ul>
     *   <li>10 (Guest) → READ</li>
     *   <li>20 (Reporter) → TRIAGE</li>
     *   <li>30 (Developer) → WRITE</li>
     *   <li>40 (Maintainer) → MAINTAIN</li>
     *   <li>50 (Owner) → ADMIN</li>
     * </ul>
     */
    static RepositoryCollaborator.Permission mapGitLabAccessLevel(int level) {
        return switch (level) {
            case 10 -> RepositoryCollaborator.Permission.READ;
            case 20 -> RepositoryCollaborator.Permission.TRIAGE;
            case 30 -> RepositoryCollaborator.Permission.WRITE;
            case 40 -> RepositoryCollaborator.Permission.MAINTAIN;
            case 50 -> RepositoryCollaborator.Permission.ADMIN;
            default -> RepositoryCollaborator.Permission.UNKNOWN;
        };
    }
}

package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab push webhook events.
 * <p>
 * On each push event, upserts the project as a {@link Repository}
 * using the embedded project metadata from the webhook payload. This ensures the repository
 * entity exists before any commit processing (future scope).
 * <p>
 * Also ensures the parent group is linked as an Organization. If the organization doesn't
 * exist yet (e.g., push arrives before full sync), it is fetched via GraphQL.
 * <p>
 * Branch deletions are skipped. All other pushes (to any branch) trigger a project upsert
 * because the project metadata is branch-independent.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabPushMessageHandler extends GitLabMessageHandler<GitLabPushEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabPushMessageHandler.class);

    private final GitLabProjectProcessor projectProcessor;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final ObjectProvider<GitLabGroupSyncService> groupSyncServiceProvider;
    private final ScopeIdResolver scopeIdResolver;

    GitLabPushMessageHandler(
        GitLabProjectProcessor projectProcessor,
        OrganizationRepository organizationRepository,
        RepositoryRepository repositoryRepository,
        ObjectProvider<GitLabGroupSyncService> groupSyncServiceProvider,
        ScopeIdResolver scopeIdResolver,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabPushEventDTO.class, deserializer, transactionTemplate);
        this.projectProcessor = projectProcessor;
        this.organizationRepository = organizationRepository;
        this.repositoryRepository = repositoryRepository;
        this.groupSyncServiceProvider = groupSyncServiceProvider;
        this.scopeIdResolver = scopeIdResolver;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.PUSH;
    }

    @Override
    protected void handleEvent(GitLabPushEventDTO event) {
        if (event.project() == null) {
            log.warn("Received push event with missing project data");
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        String safeProjectPath = sanitizeForLog(projectPath);

        if (event.isBranchDeletion()) {
            log.debug("Skipped push event: reason=branchDeletion, projectPath={}", safeProjectPath);
            return;
        }

        log.info(
            "Received push event: projectPath={}, ref={}, commits={}",
            safeProjectPath,
            event.ref(),
            event.totalCommitsCount()
        );

        // Upsert the project as a Repository entity from the webhook payload.
        // This ensures the repository exists for future commit/MR processing.
        var repository = projectProcessor.processPushEvent(event.project());

        if (repository != null) {
            log.debug(
                "Upserted project from push event: projectPath={}, repoId={}",
                safeProjectPath,
                repository.getId()
            );
            ensureOrganizationLinked(repository, projectPath);
        } else {
            log.warn("Failed to upsert project from push event: projectPath={}", safeProjectPath);
        }
    }

    /**
     * Ensures the repository is linked to its parent group organization.
     * <p>
     * Phase 1: Look up existing org in DB by group path (cheap).
     * Phase 2: If not found, fetch via GraphQL (requires scopeId resolution).
     */
    private void ensureOrganizationLinked(Repository repository, String projectPath) {
        if (repository.getOrganization() != null) {
            return;
        }

        String groupPath = extractGroupPath(projectPath);
        if (groupPath == null) {
            return; // user-owned project, no group
        }

        // Phase 1: DB lookup (most common — org was created during full sync)
        Organization org = organizationRepository.findByLoginIgnoreCase(groupPath).orElse(null);

        // Phase 2: GraphQL fetch (push arrived before full sync)
        if (org == null) {
            org = syncGroupViaApi(groupPath);
        }

        if (org != null) {
            repository.setOrganization(org);
            repositoryRepository.save(repository);
            log.debug("Linked org to repository: repoId={}, orgLogin={}", repository.getId(), groupPath);
        }
    }

    /**
     * Attempts to sync a group via the GraphQL API.
     * <p>
     * Uses {@link ScopeIdResolver} to find the workspace that monitors this group,
     * then delegates to {@link GitLabGroupSyncService#syncGroup}.
     */
    @Nullable
    private Organization syncGroupViaApi(String groupPath) {
        GitLabGroupSyncService syncService = groupSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return null;
        }

        // Try org login first, then repository name fallback
        var scopeId = scopeIdResolver
            .findScopeIdByOrgLogin(groupPath)
            .or(() -> scopeIdResolver.findScopeIdByRepositoryName(groupPath))
            .orElse(null);
        if (scopeId == null) {
            log.debug("Cannot sync group: reason=noScopeId, groupPath={}", sanitizeForLog(groupPath));
            return null;
        }

        return syncService.syncGroup(scopeId, groupPath).orElse(null);
    }

    /**
     * Extracts the parent group path from a project's full path.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "org/project"} → {@code "org"}</li>
     *   <li>{@code "org/team/subteam/project"} → {@code "org/team/subteam"}</li>
     *   <li>{@code "project"} → {@code null} (user-owned, no group)</li>
     *   <li>{@code null} → {@code null}</li>
     * </ul>
     */
    @Nullable
    static String extractGroupPath(@Nullable String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }
        int lastSlash = projectPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null;
        }
        return projectPath.substring(0, lastSlash);
    }
}

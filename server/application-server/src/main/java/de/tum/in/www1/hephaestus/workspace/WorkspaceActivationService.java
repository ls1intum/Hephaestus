package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for workspace activation and startup operations.
 * Handles activating workspaces on application startup, including metadata
 * population, sync orchestration, and NATS consumer initialization.
 */
@Service
public class WorkspaceActivationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceActivationService.class);

    // Configuration
    private final boolean isNatsEnabled;
    private final boolean runMonitoringOnStartup;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;

    // Services
    private final NatsConsumerService natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final OrganizationService organizationService;

    // Lazy-loaded dependencies (to break circular references)
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;
    private final ObjectProvider<GitHubIssueTypeSyncService> issueTypeSyncServiceProvider;
    private final ObjectProvider<GitHubSubIssueSyncService> subIssueSyncServiceProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceActivationService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        @Value("${monitoring.run-on-startup}") boolean runMonitoringOnStartup,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        NatsConsumerService natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        OrganizationService organizationService,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider,
        ObjectProvider<GitHubIssueTypeSyncService> issueTypeSyncServiceProvider,
        ObjectProvider<GitHubSubIssueSyncService> subIssueSyncServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.runMonitoringOnStartup = runMonitoringOnStartup;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.organizationService = organizationService;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.issueTypeSyncServiceProvider = issueTypeSyncServiceProvider;
        this.subIssueSyncServiceProvider = subIssueSyncServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /** Lazy accessor for GitHubDataSyncService to break circular dependency. */
    private GitHubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    /** Lazy accessor for GitHubIssueTypeSyncService. */
    private GitHubIssueTypeSyncService getIssueTypeSyncService() {
        return issueTypeSyncServiceProvider.getObject();
    }

    /** Lazy accessor for GitHubSubIssueSyncService. */
    private GitHubSubIssueSyncService getSubIssueSyncService() {
        return subIssueSyncServiceProvider.getObject();
    }

    /**
     * Prepare every workspace and start monitoring/sync routines for those that are
     * ready.
     * Intended to run after provisioning so the workspace catalog is populated.
     */
    public void activateAllWorkspaces() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        if (workspaces.isEmpty()) {
            logger.info("No workspaces found on startup; waiting for GitHub App backfill or manual provisioning.");
            return;
        }

        List<Workspace> prepared = new ArrayList<>(workspaces.size());
        for (Workspace workspace : workspaces) {
            prepared.add(ensureWorkspaceMetadata(workspace));
        }

        Set<String> organizationConsumersStarted = ConcurrentHashMap.newKeySet();

        // Activate all workspaces in parallel for scalability.
        // Each workspace's monitoring runs independently and can sync repos
        // concurrently.
        List<CompletableFuture<Void>> activationFutures = prepared
            .stream()
            .filter(workspace -> !shouldSkipActivation(workspace))
            .map(workspace ->
                CompletableFuture.runAsync(
                    () -> activateWorkspace(workspace, organizationConsumersStarted),
                    monitoringExecutor
                )
            )
            .toList();

        // Wait for all workspace activations to complete (non-blocking to main thread
        // but ensures all are started before the method returns)
        CompletableFuture.allOf(activationFutures.toArray(CompletableFuture[]::new)).exceptionally(ex -> {
            logger.error("Error during workspace activation: {}", ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * Checks if a workspace should skip activation based on its configuration.
     */
    private boolean shouldSkipActivation(Workspace workspace) {
        if (
            workspace.getGitProviderMode() == Workspace.GitProviderMode.PAT_ORG &&
            isBlank(workspace.getPersonalAccessToken())
        ) {
            logger.info(
                "Workspace id={} remains idle: PAT mode without personal access token. Configure a token or migrate to the GitHub App.",
                workspace.getId()
            );
            return true;
        }
        return false;
    }

    /**
     * Activate a single workspace: run sync operations and start NATS consumer.
     *
     * @param workspace                    the workspace to activate
     * @param organizationConsumersStarted set to track which organization consumers have been started
     */
    public void activateWorkspace(Workspace workspace, Set<String> organizationConsumersStarted) {
        if (!workspaceScopeFilter.isWorkspaceAllowed(workspace)) {
            logger.info("Workspace id={} skipped: workspace scope filters active.", workspace.getId());
            return;
        }

        // Load fresh RepositoryToMonitor entities from database to ensure sync
        // timestamps
        // are up-to-date. This is critical for respecting cooldown periods across
        // restarts.
        List<RepositoryToMonitor> repositoriesToMonitor = repositoryToMonitorRepository.findByWorkspaceId(
            workspace.getId()
        );
        var eligibleRepositories = repositoriesToMonitor
            .stream()
            .filter(workspaceScopeFilter::isRepositoryAllowed)
            .toList();

        if (runMonitoringOnStartup) {
            logger.info("Running monitoring on startup for workspace id={}", workspace.getId());

            // Set workspace context for the sync operations (enables proper logging via
            // MDC)
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(workspaceContext);
            try {
                // Sync repositories SEQUENTIALLY within each workspace.
                // This avoids race conditions for shared entities (Organization, Users)
                // and respects GitHub API rate limits per installation/PAT.
                // Workspaces themselves run in parallel using virtual threads.
                for (var repo : eligibleRepositories) {
                    try {
                        getGitHubDataSyncService().syncSyncTarget(SyncTargetFactory.create(workspace, repo));
                    } catch (Exception ex) {
                        logger.error(
                            "Error syncing repository {}: {}",
                            repo.getNameWithOwner(),
                            LoggingUtils.sanitizeForLog(ex.getMessage()),
                            ex
                        );
                    }
                }

                // TODO: User and team sync via GraphQL not yet implemented
                // Users and teams sync sequentially after all repos
                logger.info(
                    "All repositories synced for workspace id={} (user/team sync pending GraphQL migration)",
                    workspace.getId()
                );

                // Sync issue types via GraphQL (organization-level data)
                try {
                    logger.info("Teams synced, now syncing issue types for workspace id={}", workspace.getId());
                    getIssueTypeSyncService().syncIssueTypesForWorkspace(workspace.getId());
                } catch (Exception ex) {
                    logger.error("Error during syncIssueTypes: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
                }

                // Sync sub-issue relationships via GraphQL
                try {
                    logger.info("Issue types synced, now syncing sub-issues for workspace id={}", workspace.getId());
                    getSubIssueSyncService().syncSubIssuesForWorkspace(workspace.getId());
                } catch (Exception ex) {
                    logger.error("Error during syncSubIssues: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
                }

                logger.info("Finished running monitoring on startup for workspace id={}", workspace.getId());
            } finally {
                // Clear context after sync operations complete
                WorkspaceContextHolder.clearContext();
            }
        }

        // Start NATS consumer AFTER startup sync completes to avoid race conditions.
        // The startup sync ensures all entities exist before NATS starts processing
        // webhook events that might reference them.
        if (shouldUseNats(workspace)) {
            natsConsumerService.startConsumingWorkspace(workspace.getId());
        }
    }

    /**
     * Ensure workspace metadata is populated (git provider mode, account login).
     * This method persists changes if metadata was missing or derived.
     *
     * @param workspace the workspace to ensure metadata for
     * @return the workspace with metadata populated
     */
    @Transactional
    public Workspace ensureWorkspaceMetadata(Workspace workspace) {
        boolean changed = false;

        if (workspace.getGitProviderMode() == null) {
            Workspace.GitProviderMode mode = workspace.getInstallationId() != null
                ? Workspace.GitProviderMode.GITHUB_APP_INSTALLATION
                : Workspace.GitProviderMode.PAT_ORG;
            workspace.setGitProviderMode(mode);
            changed = true;
        }

        if (isBlank(workspace.getAccountLogin())) {
            String derived = deriveAccountLogin(workspace);
            if (!isBlank(derived)) {
                workspace.setAccountLogin(derived);
                changed = true;
            }
        }

        if (changed) {
            workspace = workspaceRepository.save(workspace);
        }

        return workspace;
    }

    /**
     * Derive the account login for a workspace from available sources.
     * Priority:
     * 1) Existing accountLogin if set
     * 2) Organization login from installation
     * 3) Owner from first monitored repository
     */
    String deriveAccountLogin(Workspace workspace) {
        if (!isBlank(workspace.getAccountLogin())) {
            return workspace.getAccountLogin();
        }

        String organizationLogin = null;
        Long installationId = workspace.getInstallationId();
        if (installationId != null) {
            organizationLogin = organizationService
                .getByInstallationId(installationId)
                .map(Organization::getLogin)
                .filter(login -> !isBlank(login))
                .orElse(null);
        }

        if (!isBlank(organizationLogin)) {
            return organizationLogin;
        }

        String repoOwner = workspace
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .map(this::extractOwner)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (!isBlank(repoOwner)) {
            return repoOwner;
        }

        return null;
    }

    /**
     * Extracts the owner portion from a repository name with owner (e.g., "owner/repo" -> "owner").
     */
    private String extractOwner(String nameWithOwner) {
        if (isBlank(nameWithOwner)) {
            return null;
        }
        int idx = nameWithOwner.indexOf('/');
        if (idx <= 0) {
            return null;
        }
        return nameWithOwner.substring(0, idx);
    }

    /**
     * Checks if NATS should be used for the given workspace.
     */
    private boolean shouldUseNats(Workspace workspace) {
        return isNatsEnabled && workspace != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

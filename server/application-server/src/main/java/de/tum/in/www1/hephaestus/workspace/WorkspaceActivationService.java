package de.tum.in.www1.hephaestus.workspace;

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

    private static final Logger log = LoggerFactory.getLogger(WorkspaceActivationService.class);

    // Configuration
    private final boolean isNatsEnabled;
    private final boolean runMonitoringOnStartup;

    // Core repository
    private final WorkspaceRepository workspaceRepository;

    // Services
    private final NatsConsumerService natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;

    // Lazy-loaded to break circular reference with GitHubDataSyncService
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceActivationService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        @Value("${monitoring.run-on-startup}") boolean runMonitoringOnStartup,
        WorkspaceRepository workspaceRepository,
        NatsConsumerService natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.runMonitoringOnStartup = runMonitoringOnStartup;
        this.workspaceRepository = workspaceRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /** Lazy accessor for GitHubDataSyncService to break circular dependency. */
    private GitHubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    /**
     * Prepare every workspace and start monitoring/sync routines for those that are
     * ready.
     * Intended to run after provisioning so the workspace catalog is populated.
     */
    public void activateAllWorkspaces() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        if (workspaces.isEmpty()) {
            log.info("No workspaces found on startup; waiting for GitHub App backfill or manual provisioning.");
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
            log.error("Failed to activate workspaces", ex);
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
            log.info("Skipped workspace activation: reason=patModeWithoutToken, workspaceId={}", workspace.getId());
            return true;
        }
        return false;
    }

    /**
     * Activate a single workspace: run sync operations and start NATS consumer.
     *
     * @param workspace                    the workspace to activate
     * @param organizationConsumersStarted set to track which organization consumers have been started
     *                                     (currently unused but kept for future multi-org support)
     */
    public void activateWorkspace(Workspace workspace, Set<String> organizationConsumersStarted) {
        if (!workspaceScopeFilter.isWorkspaceAllowed(workspace)) {
            log.info("Skipped workspace activation: reason=filteredByScope, workspaceId={}", workspace.getId());
            return;
        }

        if (runMonitoringOnStartup) {
            log.info("Starting monitoring on startup: workspaceId={}", workspace.getId());

            // Set workspace context for the sync operations (enables proper logging via
            // MDC)
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(workspaceContext);
            try {
                // Use the central sync orchestrator which handles:
                // 1. Organization and teams sync (via GraphQL)
                // 2. Per-repository syncs (labels, milestones, issues, PRs, comments)
                // 3. Workspace-level relationships (issue dependencies, sub-issues)
                getGitHubDataSyncService().syncAllRepositories(workspace.getId());

                log.info("Completed monitoring on startup: workspaceId={}", workspace.getId());
            } finally {
                // Clear context after sync operations complete
                WorkspaceContextHolder.clearContext();
            }
        }

        // Start NATS consumer AFTER startup sync completes to avoid race conditions.
        // The startup sync ensures all entities exist before NATS starts processing
        // webhook events that might reference them.
        if (shouldUseNats(workspace)) {
            natsConsumerService.startConsumingScope(workspace.getId());
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
     * 1) Existing accountLogin if set (always set during workspace provisioning)
     * 2) Owner from first monitored repository (fallback for legacy workspaces)
     */
    String deriveAccountLogin(Workspace workspace) {
        // Account login is set during workspace provisioning from installation events
        if (!isBlank(workspace.getAccountLogin())) {
            return workspace.getAccountLogin();
        }

        // Fallback: derive from monitored repositories (for legacy workspaces)
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

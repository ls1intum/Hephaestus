package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.gitprovider.common.GitProviderType;
import de.tum.cit.aet.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.cit.aet.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.cit.aet.hephaestus.gitprovider.sync.NatsProperties;
import de.tum.cit.aet.hephaestus.integration.sync.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final NatsProperties natsProperties;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;

    // Services — natsConsumerService absent under webhook profile (server.enabled=false);
    // activation paths that need it are themselves server-only.
    private final ObjectProvider<NatsConsumerService> natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final GitLabWorkspaceInitializationService gitLabInitService;

    // Lazy-loaded to break circular reference with sync services
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceActivationService(
        NatsProperties natsProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        WorkspaceRepository workspaceRepository,
        ObjectProvider<NatsConsumerService> natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        GitLabWorkspaceInitializationService gitLabInitService,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.natsProperties = natsProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.workspaceRepository = workspaceRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitLabInitService = gitLabInitService;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /** Lazy accessor for GitHubDataSyncService to break circular dependency. */
    private GitHubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    /**
     * Prepare every workspace and start monitoring/sync routines for those that are ready.
     * <p>
     * This method runs the full GraphQL sync for each workspace, including repositories,
     * issues, PRs, and other entities. The installation consumer has already been started
     * by the time this runs (it only needs workspaces to exist, not the full sync).
     * <p>
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

        List<Workspace> workspacesToActivate = prepared
            .stream()
            .filter(workspace -> !shouldSkipActivation(workspace))
            .toList();

        if (workspacesToActivate.isEmpty()) {
            log.info("No workspaces to activate after filtering");
            return;
        }

        log.info("Activating workspaces: count={}", workspacesToActivate.size());

        // Activate all workspaces in parallel for scalability.
        // Each workspace's monitoring runs independently and can sync repos concurrently.
        List<CompletableFuture<Void>> activationFutures = workspacesToActivate
            .stream()
            .map(workspace ->
                CompletableFuture.runAsync(() -> activateWorkspace(workspace), monitoringExecutor).exceptionally(ex -> {
                    // Handle unexpected errors (e.g., thread pool rejection) with workspace context
                    log.error(
                        "Unexpected error during workspace activation: workspaceId={}, accountLogin={}",
                        workspace.getId(),
                        workspace.getAccountLogin(),
                        ex
                    );
                    return null;
                })
            )
            .toList();

        // Log completion status (non-blocking)
        CompletableFuture.allOf(activationFutures.toArray(CompletableFuture[]::new)).whenComplete((result, ex) -> {
            // Note: Individual workspace errors are logged above, this catches aggregate issues
            if (ex != null) {
                log.error("Workspace activation completed with errors", ex);
            } else {
                log.info("Completed workspace activations: count={}", workspacesToActivate.size());
            }
        });
    }

    /**
     * Checks if a workspace should skip activation based on its configuration and status.
     */
    private boolean shouldSkipActivation(Workspace workspace) {
        // Skip non-active workspaces (SUSPENDED, PURGED) - don't waste cycles on dead workspaces
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
            log.info(
                "Skipped workspace activation: reason=notActive, workspaceId={}, status={}",
                workspace.getId(),
                workspace.getStatus()
            );
            return true;
        }

        return switch (workspace.getGitProviderMode()) {
            case PAT_ORG -> {
                if (isBlank(workspace.getPersonalAccessToken())) {
                    log.info(
                        "Skipped workspace activation: reason=patModeWithoutToken, workspaceId={}",
                        workspace.getId()
                    );
                    yield true;
                }
                yield false;
            }
            case GITHUB_APP_INSTALLATION -> false;
            case GITLAB_PAT -> {
                if (isBlank(workspace.getPersonalAccessToken())) {
                    log.info(
                        "Skipped workspace activation: reason=gitlabPatModeWithoutToken, workspaceId={}",
                        workspace.getId()
                    );
                    yield true;
                }
                yield false;
            }
            case null -> false;
        };
    }

    /** Activate a single workspace: run startup sync, then start its NATS consumer scope. */
    public void activateWorkspace(Workspace workspace) {
        // Early exit for non-active workspaces - don't waste cycles
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
            log.debug(
                "Skipped workspace activation: reason=notActive, workspaceId={}, status={}",
                workspace.getId(),
                workspace.getStatus()
            );
            return;
        }

        if (!workspaceScopeFilter.isWorkspaceAllowed(workspace)) {
            log.info("Skipped workspace activation: reason=filteredByScope, workspaceId={}", workspace.getId());
            return;
        }

        if (syncSchedulerProperties.runOnStartup()) {
            log.info("Starting monitoring on startup: workspaceId={}", workspace.getId());

            // Set workspace context for the sync operations (enables proper logging via MDC)
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(workspaceContext);
            try {
                if (workspace.getProviderType() == GitProviderType.GITLAB) {
                    // Core initialization: webhook, project discovery, org linking, monitors
                    gitLabInitService.initialize(workspace);

                    // Full data sync: memberships, issue types, per-repo data, teams
                    gitLabInitService.syncFullData(workspace);
                } else {
                    // GitHub: use the central sync orchestrator which handles:
                    // 1. Organization and teams sync (via GraphQL)
                    // 2. Per-repository syncs (labels, milestones, issues, PRs, comments)
                    // 3. Workspace-level relationships (issue types, issue dependencies, sub-issues)
                    getGitHubDataSyncService().syncAllRepositories(workspace.getId());
                }

                log.info("Completed monitoring on startup: workspaceId={}", workspace.getId());
            } catch (Exception e) {
                // Log failure with full context - don't swallow silently.
                // We continue to start NATS consumer so webhook events can still be processed.
                // Missing entities from failed sync will be handled via NAK/retry.
                log.error(
                    "Failed monitoring on startup: workspaceId={}, accountLogin={}, error={}",
                    workspace.getId(),
                    workspace.getAccountLogin(),
                    e.getMessage(),
                    e
                );
            } finally {
                // Clear context after sync operations complete
                WorkspaceContextHolder.clearContext();
            }
        }

        // Start NATS consumer AFTER startup sync completes to avoid race conditions.
        // The startup sync ensures all entities exist before NATS starts processing
        // webhook events that might reference them.
        if (shouldUseNats(workspace)) {
            natsConsumerService.ifAvailable(svc -> svc.startConsumingScope(workspace.getId()));
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
            Workspace.GitProviderMode mode =
                workspace.getInstallationId() != null
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

    /** Derives an account login for legacy workspaces from their first monitored repo's owner. */
    String deriveAccountLogin(Workspace workspace) {
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
        return natsProperties.enabled() && workspace != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

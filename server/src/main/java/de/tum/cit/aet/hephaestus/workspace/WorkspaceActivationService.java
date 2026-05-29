package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final NatsConnectionProperties natsProperties;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;

    // Services — natsConsumerService absent under webhook profile (server.enabled=false);
    // activation paths that need it are themselves server-only.
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final ConnectionService connectionService;

    /**
     * Per-kind sync triggers, collected through the SPI so workspace activation never
     * imports a specific provider's sync service. The dispatch dispatches strictly by the
     * workspace's bound {@link IntegrationKind}; an unmapped kind is treated as "no sync
     * needed for this workspace" rather than throwing — symmetric with shouldSkipActivation.
     */
    private final Map<IntegrationKind, WorkspaceDataSyncTrigger> dataSyncTriggers;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceActivationService(
        NatsConnectionProperties natsProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        WorkspaceRepository workspaceRepository,
        ObjectProvider<IntegrationNatsConsumer> natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        ConnectionService connectionService,
        List<WorkspaceDataSyncTrigger> dataSyncTriggerList,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.natsProperties = natsProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.workspaceRepository = workspaceRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.connectionService = connectionService;
        Map<IntegrationKind, WorkspaceDataSyncTrigger> map = new EnumMap<>(IntegrationKind.class);
        for (WorkspaceDataSyncTrigger t : dataSyncTriggerList) {
            map.put(t.kind(), t);
        }
        this.dataSyncTriggers = map;
        this.monitoringExecutor = monitoringExecutor;
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
     *
     * <p>Provider classification is now driven by the active {@code GITHUB} / {@code GITLAB}
     * Connection rather than the legacy {@code git_provider_mode} column. The PAT-bearing
     * variants (GitHub PAT, GitLab PAT) require a bearer credential blob; the GitHub App
     * variant mints installation tokens on demand, so its credential blob is intentionally
     * empty and we must not gate activation on it.
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

        var providerKind = connectionService.findActiveProviderKind(workspace.getId());
        if (providerKind.isEmpty()) {
            // No active SCM connection — nothing to sync until one is bound.
            return false;
        }
        return switch (providerKind.get()) {
            case GITHUB -> {
                // GitHub PAT variant requires a token; App variant mints on demand and
                // never carries a blob, so only the PAT branch gates activation.
                if (
                    connectionService.findActiveGitHubPatConfig(workspace.getId()).isPresent() &&
                    !hasBearerToken(workspace.getId(), IntegrationKind.GITHUB)
                ) {
                    log.info(
                        "Skipped workspace activation: reason=patModeWithoutToken, workspaceId={}",
                        workspace.getId()
                    );
                    yield true;
                }
                yield false;
            }
            case GITLAB -> {
                if (!hasBearerToken(workspace.getId(), IntegrationKind.GITLAB)) {
                    log.info(
                        "Skipped workspace activation: reason=gitlabPatModeWithoutToken, workspaceId={}",
                        workspace.getId()
                    );
                    yield true;
                }
                yield false;
            }
            case SLACK, OIDC_LOGIN_GITHUB, OIDC_LOGIN_GITLAB -> false;
        };
    }

    private boolean hasBearerToken(long workspaceId, IntegrationKind kind) {
        return connectionService
            .findActiveBearerToken(workspaceId, kind)
            .map(b -> b.token() != null && !b.token().isBlank())
            .orElse(false);
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

            // Set workspace context for the sync operations (enables proper logging via MDC).
            // installationId is sourced from the active GitHub App connection (if any).
            Long installationId = connectionService
                .findActiveGitHubAppConfig(workspace.getId())
                .map(ConnectionConfig.GitHubAppConfig::installationId)
                .orElse(null);
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of(), installationId);
            WorkspaceContextHolder.setContext(workspaceContext);
            try {
                IntegrationKind kind = connectionService
                    .findActiveProviderKind(workspace.getId())
                    .orElse(IntegrationKind.GITHUB);
                WorkspaceDataSyncTrigger trigger = dataSyncTriggers.get(kind);
                if (trigger == null) {
                    log.debug(
                        "Skipped startup sync: reason=noTriggerForKind, workspaceId={}, kind={}",
                        workspace.getId(),
                        kind
                    );
                } else {
                    trigger.syncAllRepositories(workspace.getId());
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
     * Ensure workspace metadata (account login) is populated. This method persists
     * changes if metadata was missing or derived.
     *
     * <p>The legacy {@code git_provider_mode} derivation has been removed — provider
     * identity is now carried by the {@code Connection} registry, which the migration
     * backfills from the legacy columns at startup. A workspace without a Connection
     * after backfill is simply "not yet bound to a provider" and {@link #shouldSkipActivation}
     * handles that path by returning {@code false} (nothing to sync, nothing to gate on).
     *
     * @param workspace the workspace to ensure metadata for
     * @return the workspace with metadata populated
     */
    @Transactional
    public Workspace ensureWorkspaceMetadata(Workspace workspace) {
        if (isBlank(workspace.getAccountLogin())) {
            String derived = deriveAccountLogin(workspace);
            if (!isBlank(derived)) {
                workspace.setAccountLogin(derived);
                workspace = workspaceRepository.save(workspace);
            }
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

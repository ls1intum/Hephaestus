package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabSyncResult;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsProperties;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;

    // Services
    private final NatsConsumerService natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;

    // Lazy-loaded to break circular reference with sync services
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;
    private final ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider;
    private final ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceActivationService(
        NatsProperties natsProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        WorkspaceRepository workspaceRepository,
        OrganizationRepository organizationRepository,
        RepositoryRepository repositoryRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        NatsConsumerService natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider,
        ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider,
        ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.natsProperties = natsProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
        this.repositoryRepository = repositoryRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.gitLabSyncServiceHolderProvider = gitLabSyncServiceHolderProvider;
        this.gitLabWebhookServiceProvider = gitLabWebhookServiceProvider;
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

        Set<String> organizationConsumersStarted = ConcurrentHashMap.newKeySet();

        // Filter workspaces that will be activated
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
                CompletableFuture.runAsync(
                    () -> activateWorkspace(workspace, organizationConsumersStarted),
                    monitoringExecutor
                ).exceptionally(ex -> {
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

    /**
     * Activate a single workspace: run sync operations and start NATS consumer.
     *
     * @param workspace                    the workspace to activate
     * @param organizationConsumersStarted set to track which organization consumers have been started
     *                                     (currently unused but kept for future multi-org support)
     */
    public void activateWorkspace(Workspace workspace, Set<String> organizationConsumersStarted) {
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
                    // Phase 0: Token rotation + webhook registration (before sync)
                    // Runs before sync so the webhook is ready before any events arrive,
                    // and token rotation ensures subsequent API calls use a fresh token.
                    var webhookService = gitLabWebhookServiceProvider.getIfAvailable();
                    if (webhookService != null) {
                        // rotateTokenIfNeeded catches exceptions internally (non-fatal)
                        webhookService.rotateTokenIfNeeded(workspace);

                        var webhookResult = webhookService.registerWebhook(workspace);
                        log.info(
                            "Webhook setup: workspaceId={}, registered={}, reason={}",
                            workspace.getId(),
                            webhookResult.registered(),
                            webhookResult.failureReason()
                        );
                    }

                    // GitLab: sync group and its projects via GraphQL
                    // Group metadata is extracted from the first page response (no extra API call)
                    var gitLabServices = gitLabSyncServiceHolderProvider.getIfAvailable();
                    var syncService = gitLabServices != null ? gitLabServices.getGroupSyncService() : null;
                    if (syncService != null) {
                        if (isBlank(workspace.getAccountLogin())) {
                            log.warn(
                                "Skipped GitLab sync: reason=missingAccountLogin, workspaceId={}",
                                workspace.getId()
                            );
                        } else {
                            GitLabSyncResult result = syncService.syncGroupProjects(
                                workspace.getId(),
                                workspace.getAccountLogin()
                            );
                            log.info(
                                "GitLab sync result: workspaceId={}, status={}, synced={}, failed={}, " +
                                    "redacted={}, reconciled={}, pages={}",
                                workspace.getId(),
                                result.status(),
                                result.synced().size(),
                                result.projectsSkipped(),
                                result.projectsRedacted(),
                                result.projectsReconciled(),
                                result.pagesCompleted()
                            );
                            // Link workspace to organization after sync (org was created during sync)
                            linkWorkspaceToOrganization(workspace);

                            // Register synced repositories as monitored for this workspace.
                            // This is analogous to ensureRepositoryMonitorForInstallation() for GitHub.
                            ensureRepositoryMonitorsForGitLab(workspace, result.synced());

                            // Sync issues and merge requests for each project.
                            // lastSyncAt is updated ONCE per repo after ALL sync phases complete,
                            // using the timestamp captured BEFORE sync starts. This ensures:
                            // 1. MR sync doesn't overwrite issue sync's timestamp
                            // 2. updatedAfter is consistent across both phases
                            var labelSyncService = gitLabServices.getLabelSyncService();
                            var milestoneSyncService = gitLabServices.getMilestoneSyncService();
                            var issueSyncService = gitLabServices.getIssueSyncService();
                            var mrSyncService = gitLabServices.getMergeRequestSyncService();

                            if (!result.synced().isEmpty()) {
                                int totalLabels = 0;
                                int totalMilestones = 0;
                                int totalIssues = 0;
                                int issueCompletedRepos = 0;
                                int totalMRs = 0;
                                int mrCompletedRepos = 0;

                                for (Repository repo : result.synced()) {
                                    // Capture updatedAfter ONCE per repo before any sync phase.
                                    OffsetDateTime updatedAfter = null;
                                    if (repo.getLastSyncAt() != null) {
                                        Instant buffered = repo.getLastSyncAt().minus(Duration.ofMinutes(5));
                                        updatedAfter = buffered.atOffset(ZoneOffset.UTC);
                                    }

                                    boolean issuesDone = false;
                                    boolean mrsDone = false;

                                    // Phase 0: Sync labels and milestones (lightweight, run first)
                                    if (labelSyncService != null) {
                                        try {
                                            SyncResult labelResult = labelSyncService.syncLabelsForRepository(
                                                workspace.getId(),
                                                repo
                                            );
                                            totalLabels += labelResult.count();
                                        } catch (Exception e) {
                                            log.warn(
                                                "Failed to sync labels for project: workspaceId={}, repoName={}",
                                                workspace.getId(),
                                                repo.getNameWithOwner(),
                                                e
                                            );
                                        }
                                    }
                                    if (milestoneSyncService != null) {
                                        try {
                                            SyncResult milestoneResult =
                                                milestoneSyncService.syncMilestonesForRepository(
                                                    workspace.getId(),
                                                    repo
                                                );
                                            totalMilestones += milestoneResult.count();
                                        } catch (Exception e) {
                                            log.warn(
                                                "Failed to sync milestones for project: workspaceId={}, repoName={}",
                                                workspace.getId(),
                                                repo.getNameWithOwner(),
                                                e
                                            );
                                        }
                                    }

                                    // Phase 1: Sync issues
                                    if (issueSyncService != null) {
                                        try {
                                            SyncResult issueResult = issueSyncService.syncIssues(
                                                workspace.getId(),
                                                repo,
                                                updatedAfter
                                            );
                                            totalIssues += issueResult.count();
                                            issuesDone = issueResult.isCompleted();
                                            if (issuesDone) issueCompletedRepos++;
                                        } catch (Exception e) {
                                            log.warn(
                                                "Failed to sync issues for project: workspaceId={}, repoName={}",
                                                workspace.getId(),
                                                repo.getNameWithOwner(),
                                                e
                                            );
                                        }
                                    }

                                    // Phase 2: Sync merge requests
                                    if (mrSyncService != null) {
                                        try {
                                            SyncResult mrResult = mrSyncService.syncMergeRequests(
                                                workspace.getId(),
                                                repo,
                                                updatedAfter
                                            );
                                            totalMRs += mrResult.count();
                                            mrsDone = mrResult.isCompleted();
                                            if (mrsDone) mrCompletedRepos++;
                                        } catch (Exception e) {
                                            log.warn(
                                                "Failed to sync merge requests for project: workspaceId={}, repoName={}",
                                                workspace.getId(),
                                                repo.getNameWithOwner(),
                                                e
                                            );
                                        }
                                    }

                                    // Update lastSyncAt only when every enabled sync phase completed for this repo
                                    boolean allEnabledPhasesCompleted =
                                        (issueSyncService == null || issuesDone) && (mrSyncService == null || mrsDone);
                                    if (allEnabledPhasesCompleted) {
                                        repositoryRepository.updateLastSyncAt(repo.getId(), Instant.now());
                                    }
                                }

                                if (labelSyncService != null || milestoneSyncService != null) {
                                    log.info(
                                        "GitLab label/milestone sync complete: workspaceId={}, projects={}, totalLabels={}, totalMilestones={}",
                                        workspace.getId(),
                                        result.synced().size(),
                                        totalLabels,
                                        totalMilestones
                                    );
                                }
                                if (issueSyncService != null) {
                                    log.info(
                                        "GitLab issue sync complete: workspaceId={}, projects={}, completedRepos={}, totalIssues={}",
                                        workspace.getId(),
                                        result.synced().size(),
                                        issueCompletedRepos,
                                        totalIssues
                                    );
                                }
                                if (mrSyncService != null) {
                                    log.info(
                                        "GitLab MR sync complete: workspaceId={}, projects={}, completedRepos={}, totalMRs={}",
                                        workspace.getId(),
                                        result.synced().size(),
                                        mrCompletedRepos,
                                        totalMRs
                                    );
                                }
                            }
                        }
                    } else {
                        log.warn(
                            "Skipped GitLab sync: reason=syncServiceUnavailable, workspaceId={}",
                            workspace.getId()
                        );
                    }
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
        return natsProperties.enabled() && workspace != null;
    }

    /**
     * Links a workspace to its organization after sync completes.
     * The organization is created during sync but not linked to the workspace at that point
     * because the sync service doesn't have access to the workspace entity.
     */
    @Transactional
    void linkWorkspaceToOrganization(Workspace workspace) {
        if (workspace.getOrganization() != null || isBlank(workspace.getAccountLogin())) {
            return;
        }
        organizationRepository
            .findByLoginIgnoreCase(workspace.getAccountLogin())
            .ifPresent(org -> {
                workspaceRepository
                    .findById(workspace.getId())
                    .ifPresent(current -> {
                        if (current.getOrganization() == null) {
                            current.setOrganization(org);
                            workspaceRepository.save(current);
                            log.info(
                                "Linked organization to workspace: orgId={}, workspaceId={}",
                                org.getId(),
                                current.getId()
                            );
                        }
                    });
            });
    }

    /**
     * Ensures each synced GitLab repository has a corresponding {@link RepositoryToMonitor} entry.
     * Analogous to {@code ensureRepositoryMonitorForInstallation()} for GitHub App installations.
     */
    @Transactional
    void ensureRepositoryMonitorsForGitLab(Workspace workspace, List<Repository> syncedRepos) {
        // Bulk-fetch existing monitors to avoid N+1 queries
        Set<String> existing = repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .collect(java.util.stream.Collectors.toSet());

        int created = 0;
        for (Repository repo : syncedRepos) {
            String nwo = repo.getNameWithOwner();
            if (existing.contains(nwo)) {
                continue;
            }
            RepositoryToMonitor monitor = new RepositoryToMonitor();
            monitor.setNameWithOwner(nwo);
            monitor.setWorkspace(workspace);
            repositoryToMonitorRepository.save(monitor);
            created++;
        }
        if (created > 0) {
            log.info(
                "Created repository monitors for GitLab workspace: workspaceId={}, created={}, total={}",
                workspace.getId(),
                created,
                syncedRepos.size()
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

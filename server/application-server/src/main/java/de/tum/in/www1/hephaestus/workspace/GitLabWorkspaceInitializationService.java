package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabRateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabSyncResult;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes a GitLab PAT workspace by discovering all repositories in its group.
 *
 * <p>Encapsulates the GitLab-specific initialization sequence that runs both at
 * workspace creation time (async, fire-and-forget) and during startup activation.
 * The sequence:
 * <ol>
 *   <li>Token rotation (if expiring soon)</li>
 *   <li>Webhook registration (idempotent)</li>
 *   <li>Group project sync via GraphQL (discovers all projects in group + subgroups)</li>
 *   <li>Organization linking (workspace → synced Organization entity)</li>
 *   <li>Repository monitor creation (RepositoryToMonitor entries for each project)</li>
 * </ol>
 *
 * <p>This service is intentionally in the {@code workspace} package because it bridges
 * workspace lifecycle (creation/activation) with gitprovider sync services. The dependency
 * direction is always {@code workspace → gitprovider}, never the reverse.
 *
 * <p><b>Transaction note:</b> {@link #linkWorkspaceToOrganization} is {@code @Transactional}
 * and {@code public} so that Spring's proxy intercepts calls from the non-transactional
 * {@link #initialize} method. {@link #ensureRepositoryMonitors} is intentionally
 * NOT {@code @Transactional} — each monitor save auto-commits independently so that
 * a failure on one does not roll back previously created monitors.
 *
 * @see WorkspaceActivationService
 * @see de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService
 */
@Service
public class GitLabWorkspaceInitializationService {

    private static final Logger log = LoggerFactory.getLogger(GitLabWorkspaceInitializationService.class);

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;

    // Configuration
    private final NatsProperties natsProperties;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Services
    private final NatsConsumerService natsConsumerService;

    // Lazy-loaded: optional GitLab beans gated by @ConditionalOnProperty
    private final ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider;
    private final ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider;
    private final ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public GitLabWorkspaceInitializationService(
        WorkspaceRepository workspaceRepository,
        OrganizationRepository organizationRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        NatsProperties natsProperties,
        SyncSchedulerProperties syncSchedulerProperties,
        NatsConsumerService natsConsumerService,
        ObjectProvider<GitLabSyncServiceHolder> gitLabSyncServiceHolderProvider,
        ObjectProvider<GitLabWebhookService> gitLabWebhookServiceProvider,
        ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.natsProperties = natsProperties;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.natsConsumerService = natsConsumerService;
        this.gitLabSyncServiceHolderProvider = gitLabSyncServiceHolderProvider;
        this.gitLabWebhookServiceProvider = gitLabWebhookServiceProvider;
        this.rateLimitTrackerProvider = rateLimitTrackerProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /**
     * Triggers GitLab workspace initialization asynchronously.
     *
     * <p>Used at workspace creation time so the HTTP response returns immediately
     * while discovery runs in the background. Also starts the NATS consumer after
     * initialization so webhook events for the new workspace are processed.
     *
     * @param workspaceId the ID of the newly created GITLAB_PAT workspace
     */
    public void initializeAsync(Long workspaceId) {
        monitoringExecutor.submit(() -> {
            try {
                Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
                if (workspace == null) {
                    log.warn(
                        "Skipped async GitLab initialization: reason=workspaceNotFound, workspaceId={}",
                        workspaceId
                    );
                    return;
                }

                // Set workspace context for entire lifecycle (init + sync + NATS).
                // initialize() checks contextOwner and won't double-set/clear.
                WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, Set.of());
                WorkspaceContextHolder.setContext(context);
                try {
                    initialize(workspace);
                    syncFullData(workspace);
                    startNatsConsumer(workspace);
                } finally {
                    WorkspaceContextHolder.clearContext();
                }
            } catch (Exception e) {
                log.error("Failed async GitLab workspace initialization: workspaceId={}", workspaceId, e);
            }
        });
    }

    /**
     * Runs the core GitLab initialization sequence for a workspace:
     * webhook setup, project discovery, organization linking, and monitor creation.
     *
     * <p>Called both from {@link #initializeAsync(Long)} (creation time) and from
     * {@link WorkspaceActivationService#activateWorkspace} (startup time).
     *
     * <p>Each phase is isolated: a failure in webhook registration does not prevent
     * project discovery.
     *
     * <p><b>Note:</b> This method does NOT start the NATS consumer. Callers are responsible
     * for starting NATS at the appropriate point in their lifecycle. At creation time,
     * {@link #initializeAsync} starts it after initialization. At startup time,
     * {@link WorkspaceActivationService} starts it after full sync completes.
     *
     * @param workspace the GITLAB_PAT workspace to initialize
     */
    public void initialize(Workspace workspace) {
        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.GITLAB_PAT) {
            return;
        }

        if (isBlank(workspace.getPersonalAccessToken())) {
            log.warn("Skipped GitLab initialization: reason=missingToken, workspaceId={}", workspace.getId());
            return;
        }

        if (isBlank(workspace.getAccountLogin())) {
            log.warn("Skipped GitLab initialization: reason=missingAccountLogin, workspaceId={}", workspace.getId());
            return;
        }

        // Only set context if not already set (avoids clearing caller's context)
        boolean contextOwner = WorkspaceContextHolder.getContext() == null;
        if (contextOwner) {
            WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(context);
        }
        try {
            log.info(
                "Starting GitLab workspace initialization: workspaceId={}, accountLogin={}",
                workspace.getId(),
                LoggingUtils.sanitizeForLog(workspace.getAccountLogin())
            );

            // Phase 0: Token rotation + webhook registration
            setupWebhook(workspace);

            // Phase 1: Discover group projects via GraphQL
            List<Repository> syncedRepos = discoverGroupProjects(workspace);

            // Phase 2: Link organization + create monitors
            if (!syncedRepos.isEmpty()) {
                linkWorkspaceToOrganization(workspace);
                int created = ensureRepositoryMonitors(workspace, syncedRepos);
                // Update NATS consumer subscriptions to include newly discovered repos
                if (created > 0 && natsProperties.enabled()) {
                    natsConsumerService.updateScopeConsumer(workspace.getId());
                }
            }

            log.info(
                "Completed GitLab workspace initialization: workspaceId={}, repos={}",
                workspace.getId(),
                syncedRepos.size()
            );
        } finally {
            if (contextOwner) {
                WorkspaceContextHolder.clearContext();
            }
        }
    }

    /**
     * Runs token rotation and webhook registration.
     * Errors are logged but do not abort initialization.
     */
    private void setupWebhook(Workspace workspace) {
        var webhookService = gitLabWebhookServiceProvider.getIfAvailable();
        if (webhookService == null) {
            return;
        }

        try {
            webhookService.rotateTokenIfNeeded(workspace);
        } catch (Exception e) {
            log.warn("Token rotation failed (non-fatal): workspaceId={}", workspace.getId(), e);
        }

        try {
            var webhookResult = webhookService.registerWebhook(workspace);
            if (webhookResult.registered()) {
                log.info(
                    "Webhook setup: workspaceId={}, registered=true, webhookId={}",
                    workspace.getId(),
                    webhookResult.webhookId()
                );
            } else {
                log.warn(
                    "Webhook setup: workspaceId={}, registered=false, reason={}",
                    workspace.getId(),
                    LoggingUtils.sanitizeForLog(webhookResult.failureReason())
                );
            }
        } catch (Exception e) {
            log.warn("Webhook registration failed (non-fatal): workspaceId={}", workspace.getId(), e);
        }
    }

    /**
     * Discovers all projects in the workspace's GitLab group via GraphQL.
     *
     * @return list of synced repositories (empty on failure, never null)
     */
    private List<Repository> discoverGroupProjects(Workspace workspace) {
        var gitLabServices = gitLabSyncServiceHolderProvider.getIfAvailable();
        var syncService = gitLabServices != null ? gitLabServices.getGroupSyncService() : null;

        if (syncService == null) {
            log.warn(
                "Skipped GitLab project discovery: reason=syncServiceUnavailable, workspaceId={}",
                workspace.getId()
            );
            return Collections.emptyList();
        }

        try {
            GitLabSyncResult result = syncService.syncGroupProjects(workspace.getId(), workspace.getAccountLogin());
            log.info(
                "GitLab project discovery: workspaceId={}, status={}, synced={}, failed={}, pages={}",
                workspace.getId(),
                result.status(),
                result.synced().size(),
                result.projectsSkipped(),
                result.pagesCompleted()
            );
            return result.synced();
        } catch (Exception e) {
            log.error("Failed GitLab project discovery: workspaceId={}", workspace.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Links the workspace to its Organization entity (created during sync).
     *
     * <p>This method is {@code public} and {@code @Transactional} so that Spring's
     * transactional proxy intercepts calls from the non-transactional {@link #initialize}.
     */
    @Transactional
    public void linkWorkspaceToOrganization(Workspace workspace) {
        if (workspace.getOrganization() != null || isBlank(workspace.getAccountLogin())) {
            return;
        }
        organizationRepository
            .findByLoginIgnoreCase(workspace.getAccountLogin())
            .ifPresent(org -> {
                // Check if another workspace already references this organization
                // (workspace.organization_id has a unique constraint)
                if (
                    workspaceRepository.existsByOrganizationId(org.getId()) &&
                    !workspaceRepository.existsByIdAndOrganizationId(workspace.getId(), org.getId())
                ) {
                    log.warn(
                        "Organization already linked to another workspace: orgId={}, workspaceId={}",
                        org.getId(),
                        workspace.getId()
                    );
                    return;
                }

                // Re-read to get latest state (may have been linked concurrently)
                workspaceRepository
                    .findById(workspace.getId())
                    .ifPresent(current -> {
                        if (current.getOrganization() == null) {
                            current.setOrganization(org);
                            workspaceRepository.save(current);
                            // Update the in-memory reference for subsequent phases
                            workspace.setOrganization(org);
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
     * Creates {@link RepositoryToMonitor} entries for each synced repository.
     * Idempotent: existing monitors are not duplicated.
     *
     * <p>This method is {@code public} so that callers outside this class may invoke
     * it directly for re-initialization. Each save runs in its own auto-committed
     * transaction (no {@code @Transactional}) so that a failure on one monitor
     * does not roll back previously created ones.
     *
     * @return number of newly created monitors
     */
    public int ensureRepositoryMonitors(Workspace workspace, List<Repository> syncedRepos) {
        Set<String> existing = repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .collect(Collectors.toSet());

        int created = 0;
        for (Repository repo : syncedRepos) {
            String nwo = repo.getNameWithOwner();
            if (nwo == null || existing.contains(nwo)) {
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
                "Created repository monitors: workspaceId={}, created={}, total={}",
                workspace.getId(),
                created,
                syncedRepos.size()
            );
        }
        return created;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full data sync: memberships, issue types, per-repo data, teams
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Runs the full GitLab data sync for a workspace: memberships, issue types,
     * per-repo data (labels, milestones, issues, MRs, collaborators, commits),
     * sub-issues, dependencies, and teams.
     *
     * <p>Called both at creation time (from {@link #initializeAsync}) and at
     * startup (from {@link WorkspaceActivationService#activateWorkspace}).
     */
    public void syncFullData(Workspace workspace) {
        var gitLabServices = gitLabSyncServiceHolderProvider.getIfAvailable();
        if (gitLabServices == null) {
            log.debug(
                "Skipped GitLab full data sync: reason=syncServicesUnavailable, workspaceId={}",
                workspace.getId()
            );
            return;
        }
        if (isBlank(workspace.getAccountLogin())) {
            return;
        }

        // Sync group memberships — look up organization via repository to avoid lazy proxy issues
        var memberSyncService = gitLabServices.getGroupMemberSyncService();
        if (memberSyncService != null) {
            organizationRepository
                .findByLoginIgnoreCase(workspace.getAccountLogin())
                .ifPresent(org -> {
                    try {
                        int membersSynced = memberSyncService.syncGroupMemberships(
                            workspace.getId(),
                            workspace.getAccountLogin(),
                            org
                        );
                        log.info(
                            "GitLab membership sync: workspaceId={}, membersSynced={}",
                            workspace.getId(),
                            membersSynced
                        );
                    } catch (Exception e) {
                        log.warn("Failed membership sync: workspaceId={}", workspace.getId(), e);
                    }
                });
        }

        // Sync issue types (org-level, lightweight)
        var issueTypeSyncService = gitLabServices.getIssueTypeSyncService();
        if (issueTypeSyncService != null) {
            try {
                int issueTypes = issueTypeSyncService.syncIssueTypesForGroup(
                    workspace.getId(),
                    workspace.getAccountLogin()
                );
                log.info("GitLab issue type sync: workspaceId={}, types={}", workspace.getId(), issueTypes);
            } catch (Exception e) {
                log.warn("Failed issue type sync: workspaceId={}", workspace.getId(), e);
            }
        }

        // Resolve repos via workspace monitors (includes subgroup repos)
        List<Repository> repos = repositoryRepository.findAllByWorkspaceMonitors(workspace.getId());

        if (repos.isEmpty()) {
            log.debug("Skipped GitLab repo sync: reason=noRepos, workspaceId={}", workspace.getId());
        } else {
            syncGitLabRepositories(workspace, gitLabServices, repos);
            syncGitLabPostRepo(workspace, gitLabServices, repos);
        }

        // Teams (subgroups)
        var teamSyncService = gitLabServices.getTeamSyncService();
        if (teamSyncService != null) {
            try {
                int teamsCount = teamSyncService.syncTeamsForGroup(workspace.getId(), workspace.getAccountLogin());
                log.info("GitLab team sync complete: workspaceId={}, teams={}", workspace.getId(), teamsCount);
            } catch (Exception e) {
                log.warn("Failed to sync teams: workspaceId={}", workspace.getId(), e);
            }
        }
    }

    /**
     * Syncs per-repository data (labels, milestones, issues, MRs, collaborators, commits).
     *
     * <p>Integrates rate limit awareness matching the cron scheduler pattern:
     * checks {@link GitLabRateLimitTracker#isCritical} before each repository and
     * blocks via {@link GitLabRateLimitTracker#waitIfNeeded} when the remaining
     * budget drops below the critical threshold.
     *
     * <p>Applies cooldown for slowly-changing entities (labels, milestones, collaborators):
     * if a repository was synced within {@code syncSchedulerProperties.cooldownMinutes()},
     * these entities are skipped. Issues and MRs always sync incrementally via
     * {@code updatedAfter}.
     */
    private void syncGitLabRepositories(
        Workspace workspace,
        GitLabSyncServiceHolder gitLabServices,
        List<Repository> repos
    ) {
        var labelSyncService = gitLabServices.getLabelSyncService();
        var milestoneSyncService = gitLabServices.getMilestoneSyncService();
        var issueSyncService = gitLabServices.getIssueSyncService();
        var mrSyncService = gitLabServices.getMergeRequestSyncService();
        var collaboratorSyncService = gitLabServices.getCollaboratorSyncService();
        var commitSyncService = gitLabServices.getCommitSyncService();
        var commitBackfillService = gitLabServices.getCommitBackfillService();
        var commitMrLinker = gitLabServices.getCommitMergeRequestLinker();

        GitLabRateLimitTracker rateLimitTracker = rateLimitTrackerProvider.getIfAvailable();
        Instant cooldownThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);

        int totalLabels = 0,
            totalMilestones = 0,
            totalIssues = 0,
            totalMRs = 0;
        int totalCollaborators = 0,
            totalCommits = 0;
        int skippedCooldown = 0;

        for (Repository repo : repos) {
            // Rate limit gate: block if remaining budget is critical
            if (rateLimitTracker != null && rateLimitTracker.isCritical(workspace.getId())) {
                log.info(
                    "Rate limit critical, waiting: workspaceId={}, remaining={}",
                    workspace.getId(),
                    rateLimitTracker.getRemaining(workspace.getId())
                );
                try {
                    rateLimitTracker.waitIfNeeded(workspace.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Rate limit wait interrupted, stopping repo sync: workspaceId={}", workspace.getId());
                    break;
                }
            }

            OffsetDateTime updatedAfter = null;
            if (repo.getLastSyncAt() != null) {
                Instant buffered = repo.getLastSyncAt().minus(Duration.ofMinutes(5));
                updatedAfter = buffered.atOffset(ZoneOffset.UTC);
            }

            // Cooldown: skip labels/milestones/collaborators if recently synced
            boolean skipSlowChanging = repo.getLastSyncAt() != null && repo.getLastSyncAt().isAfter(cooldownThreshold);
            if (skipSlowChanging) {
                skippedCooldown++;
            }

            boolean issuesDone = false;
            boolean mrsDone = false;

            if (!skipSlowChanging && labelSyncService != null) {
                try {
                    SyncResult r = labelSyncService.syncLabelsForRepository(workspace.getId(), repo);
                    totalLabels += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed label sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            if (!skipSlowChanging && milestoneSyncService != null) {
                try {
                    SyncResult r = milestoneSyncService.syncMilestonesForRepository(workspace.getId(), repo);
                    totalMilestones += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed milestone sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            if (issueSyncService != null) {
                try {
                    SyncResult r = issueSyncService.syncIssues(workspace.getId(), repo, updatedAfter);
                    totalIssues += r.count();
                    issuesDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn(
                        "Failed issue sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            if (mrSyncService != null) {
                try {
                    SyncResult r = mrSyncService.syncMergeRequests(workspace.getId(), repo, updatedAfter);
                    totalMRs += r.count();
                    mrsDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn("Failed MR sync: workspaceId={}, repo={}", workspace.getId(), repo.getNameWithOwner(), e);
                }
            }
            if (!skipSlowChanging && collaboratorSyncService != null) {
                try {
                    SyncResult r = collaboratorSyncService.syncCollaboratorsForRepository(workspace.getId(), repo);
                    totalCollaborators += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed collaborator sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            // Prefer JGit backfill (provides diff stats + file changes);
            // fall back to REST API when git is not enabled
            if (commitBackfillService != null) {
                try {
                    SyncResult r = commitBackfillService.backfillCommits(workspace.getId(), repo);
                    totalCommits += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed commit backfill: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            } else if (commitSyncService != null) {
                try {
                    SyncResult r = commitSyncService.syncCommitsForRepository(workspace.getId(), repo, updatedAfter);
                    totalCommits += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed commit sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            // Link commits to their merge requests (must run after both commits and MRs have synced)
            if (commitMrLinker != null) {
                try {
                    commitMrLinker.linkCommitsForRepository(workspace.getId(), repo);
                } catch (Exception e) {
                    log.warn(
                        "Failed commit→MR linking: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            boolean allDone = (issueSyncService == null || issuesDone) && (mrSyncService == null || mrsDone);
            if (allDone) {
                repositoryRepository.updateLastSyncAt(repo.getId(), Instant.now());
            }
        }

        log.info(
            "GitLab repo sync complete: workspaceId={}, repos={}, labels={}, milestones={}, issues={}, mrs={}, " +
                "collaborators={}, commits={}, cooldownSkipped={}",
            workspace.getId(),
            repos.size(),
            totalLabels,
            totalMilestones,
            totalIssues,
            totalMRs,
            totalCollaborators,
            totalCommits,
            skippedCooldown
        );
    }

    /**
     * Post-repo sync: sub-issues and issue dependencies.
     */
    private void syncGitLabPostRepo(
        Workspace workspace,
        GitLabSyncServiceHolder gitLabServices,
        List<Repository> repos
    ) {
        var subIssueSync = gitLabServices.getSubIssueSyncService();
        var depSync = gitLabServices.getIssueDependencySyncService();
        if (subIssueSync == null && depSync == null) return;

        int totalSubIssues = 0,
            totalDeps = 0;
        for (Repository repo : repos) {
            if (subIssueSync != null) {
                try {
                    SyncResult r = subIssueSync.syncSubIssuesForRepository(workspace.getId(), repo);
                    totalSubIssues += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed sub-issue sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            if (depSync != null) {
                try {
                    SyncResult r = depSync.syncDependenciesForRepository(workspace.getId(), repo);
                    totalDeps += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed dependency sync: workspaceId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
        }
        if (totalSubIssues > 0 || totalDeps > 0) {
            log.info(
                "GitLab post-repo sync: workspaceId={}, subIssues={}, deps={}",
                workspace.getId(),
                totalSubIssues,
                totalDeps
            );
        }
    }

    /**
     * Starts the NATS consumer for webhook event processing if NATS is enabled.
     */
    private void startNatsConsumer(Workspace workspace) {
        if (natsProperties.enabled()) {
            natsConsumerService.startConsumingScope(workspace.getId());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.gitlab.GitLabCommitSyncService;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabRateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.gitlab.GitLabIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.gitlab.GitLabIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.gitlab.GitLabLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.GitLabMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupMemberSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabGroupSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.gitlab.GitLabSyncResult;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.GitLabMergeRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.gitlab.GitLabCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.gitlab.GitLabSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.gitlab.GitLabTeamSyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceScopeFilter;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic GitLab data synchronization across all GitLab scopes.
 *
 * <h2>Purpose</h2>
 * Provides eventual consistency for GitLab data by running scheduled re-sync jobs.
 * This closes the critical gap where missed webhooks (restarts, NATS outages,
 * GitLab's 40-failure auto-disable) cause permanent data drift.
 *
 * <h2>Architecture</h2>
 * Mirrors {@link GitHubDataSyncScheduler} but uses GitLab-specific sync services.
 * Runs on the same cron schedule, processing all active GitLab workspaces in parallel.
 * Each workspace syncs: group projects, memberships, labels, milestones, issues,
 * merge requests, and teams.
 *
 * <h2>Thread Safety</h2>
 * Same guarantees as {@link GitHubDataSyncScheduler}: single-threaded scheduling,
 * parallel workspace processing via virtual threads, per-workspace error isolation.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitLabDataSyncScheduler.class);

    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider;
    private final ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final Executor monitoringExecutor;

    public GitLabDataSyncScheduler(
        WorkspaceRepository workspaceRepository,
        OrganizationRepository organizationRepository,
        RepositoryRepository repositoryRepository,
        WorkspaceScopeFilter workspaceScopeFilter,
        ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider,
        ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider,
        SyncSchedulerProperties syncSchedulerProperties,
        @Qualifier("monitoringExecutor") Executor monitoringExecutor
    ) {
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
        this.repositoryRepository = repositoryRepository;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.syncServiceHolderProvider = syncServiceHolderProvider;
        this.rateLimitTrackerProvider = rateLimitTrackerProvider;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.monitoringExecutor = monitoringExecutor;
    }

    @PostConstruct
    void logSyncConfiguration() {
        log.info(
            "GitLab incremental sync config: cron={} (syncs groups, memberships, labels, milestones, issues, MRs, teams)",
            syncSchedulerProperties.cron()
        );
    }

    /**
     * Scheduled job to sync GitLab data for all active GitLab workspaces.
     * Runs on the same cron as GitHub sync (default: daily at 3 AM).
     */
    @Scheduled(cron = "${hephaestus.sync.cron}")
    public void syncDataCron() {
        List<Workspace> gitLabWorkspaces = workspaceRepository
            .findAll()
            .stream()
            .filter(ws -> ws.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .filter(ws -> ws.getProviderType() == GitProviderType.GITLAB)
            .filter(workspaceScopeFilter::isWorkspaceAllowed)
            .toList();

        if (gitLabWorkspaces.isEmpty()) {
            log.debug("No active GitLab workspaces to sync");
            return;
        }

        log.info("Starting scheduled GitLab sync: workspaceCount={}", gitLabWorkspaces.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = gitLabWorkspaces
            .stream()
            .map(workspace ->
                CompletableFuture.runAsync(() -> syncWorkspace(workspace), monitoringExecutor).whenComplete(
                    (result, error) -> {
                        if (error != null) {
                            failureCount.incrementAndGet();
                        } else {
                            successCount.incrementAndGet();
                        }
                    }
                )
            )
            .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(futures).get();
            log.info(
                "Completed scheduled GitLab sync: workspaceCount={}, successful={}, failed={}",
                gitLabWorkspaces.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                "GitLab sync interrupted: workspaceCount={}, successful={}, failed={}",
                gitLabWorkspaces.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (ExecutionException e) {
            log.error("Unexpected error during scheduled GitLab sync", e);
        }
    }

    private void syncWorkspace(Workspace workspace) {
        Long scopeId = workspace.getId();
        String safeLogin = sanitizeForLog(workspace.getAccountLogin());

        WorkspaceContext ctx = WorkspaceContext.fromWorkspace(workspace, Set.of());
        WorkspaceContextHolder.setContext(ctx);
        try {
            log.info("Starting GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin);

            GitLabSyncServiceHolder services = syncServiceHolderProvider.getIfAvailable();
            if (services == null) {
                log.warn("GitLab sync services unavailable, skipping: scopeId={}", scopeId);
                return;
            }

            if (workspace.getAccountLogin() == null || workspace.getAccountLogin().isBlank()) {
                log.warn("No accountLogin for GitLab workspace, skipping: scopeId={}", scopeId);
                return;
            }

            // Phase 1: Sync group projects (discovers new repos, removes deleted ones)
            syncGroupProjects(services, workspace);

            // Phase 2: Sync group memberships
            syncGroupMembers(services, workspace);

            // Phase 2.5: Sync issue types (org-level, before per-repo sync)
            syncIssueTypes(services, workspace);

            // Phase 3: Per-repository sync (labels, milestones, issues, MRs, collaborators)
            syncRepositories(services, workspace);

            // Phase 4: Post-repo sync (sub-issues, dependencies — needs issues to exist)
            syncPostRepo(services, workspace);

            // Phase 5: Sync teams (subgroups)
            syncTeams(services, workspace);

            log.info("Completed GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin);
        } catch (Exception e) {
            log.error("Failed GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin, e);
        } finally {
            WorkspaceContextHolder.clearContext();
        }
    }

    private void syncGroupProjects(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabGroupSyncService groupSync = services.getGroupSyncService();
        if (groupSync == null) return;

        try {
            GitLabSyncResult result = groupSync.syncGroupProjects(workspace.getId(), workspace.getAccountLogin());
            log.info(
                "GitLab group project sync: scopeId={}, status={}, synced={}, pages={}",
                workspace.getId(),
                result.status(),
                result.synced().size(),
                result.pagesCompleted()
            );

            // Stale repo cleanup: only when sync completed normally
            if (result.status() == GitLabSyncResult.Status.COMPLETED) {
                removeStaleRepositories(workspace, result);
            }
        } catch (Exception e) {
            log.error("Failed GitLab group project sync: scopeId={}", workspace.getId(), e);
        }
    }

    /**
     * Removes repositories that exist in the database but were not found during
     * the latest group project sync. Guards against false positives by only running
     * when the sync completed fully (all pages fetched).
     */
    private void removeStaleRepositories(Workspace workspace, GitLabSyncResult result) {
        Long providerId = getGitLabProviderId(workspace);
        if (providerId == null) return;

        Set<Long> syncedNativeIds = result.synced().stream().map(Repository::getNativeId).collect(Collectors.toSet());

        List<Repository> existingRepos = repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
            workspace.getAccountLogin(),
            providerId
        );

        int removed = 0;
        for (Repository repo : existingRepos) {
            if (
                repo.getProvider() != null &&
                repo.getProvider().getId().equals(providerId) &&
                !syncedNativeIds.contains(repo.getNativeId())
            ) {
                log.info(
                    "Removing stale repository: repoId={}, name={}, nativeId={}",
                    repo.getId(),
                    sanitizeForLog(repo.getNameWithOwner()),
                    repo.getNativeId()
                );
                repositoryRepository.delete(repo);
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Removed stale repositories: scopeId={}, count={}", workspace.getId(), removed);
        }
    }

    private void syncGroupMembers(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabGroupMemberSyncService memberSync = services.getGroupMemberSyncService();
        if (memberSync == null) return;

        try {
            organizationRepository
                .findByLoginIgnoreCase(workspace.getAccountLogin())
                .ifPresent(org -> {
                    int count = memberSync.syncGroupMemberships(workspace.getId(), workspace.getAccountLogin(), org);
                    log.info("GitLab membership sync: scopeId={}, membersSynced={}", workspace.getId(), count);
                });
        } catch (Exception e) {
            log.error("Failed GitLab membership sync: scopeId={}", workspace.getId(), e);
        }
    }

    private void syncIssueTypes(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabIssueTypeSyncService issueTypeSync = services.getIssueTypeSyncService();
        if (issueTypeSync == null) return;

        try {
            int count = issueTypeSync.syncIssueTypesForGroup(workspace.getId(), workspace.getAccountLogin());
            log.info("GitLab issue type sync: scopeId={}, types={}", workspace.getId(), count);
        } catch (Exception e) {
            log.error("Failed GitLab issue type sync: scopeId={}", workspace.getId(), e);
        }
    }

    private void syncRepositories(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabLabelSyncService labelSync = services.getLabelSyncService();
        GitLabMilestoneSyncService milestoneSync = services.getMilestoneSyncService();
        GitLabIssueSyncService issueSync = services.getIssueSyncService();
        GitLabMergeRequestSyncService mrSync = services.getMergeRequestSyncService();
        GitLabCollaboratorSyncService collaboratorSync = services.getCollaboratorSyncService();
        GitLabCommitSyncService commitSync = services.getCommitSyncService();

        // Find all repositories monitored by this workspace
        List<Repository> repos = repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
            workspace.getAccountLogin(),
            getGitLabProviderId(workspace)
        );

        if (repos.isEmpty()) {
            log.debug("No repositories to sync for GitLab workspace: scopeId={}", workspace.getId());
            return;
        }

        GitLabRateLimitTracker rateLimitTracker = rateLimitTrackerProvider.getIfAvailable();
        int totalLabels = 0,
            totalMilestones = 0,
            totalIssues = 0,
            totalMRs = 0,
            totalCollaborators = 0,
            totalCommits = 0;

        for (Repository repo : repos) {
            // Wait for rate limit if critical
            if (rateLimitTracker != null && rateLimitTracker.isCritical(workspace.getId())) {
                log.info(
                    "Rate limit critical, waiting: scopeId={}, remaining={}",
                    workspace.getId(),
                    rateLimitTracker.getRemaining(workspace.getId())
                );
                try {
                    rateLimitTracker.waitIfNeeded(workspace.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Rate limit wait interrupted, stopping repo sync: scopeId={}", workspace.getId());
                    break;
                }
            }

            // Capture updatedAfter ONCE per repo before any sync phase
            OffsetDateTime updatedAfter = null;
            if (repo.getLastSyncAt() != null) {
                Instant buffered = repo.getLastSyncAt().minus(Duration.ofMinutes(5));
                updatedAfter = buffered.atOffset(ZoneOffset.UTC);
            }

            boolean issuesDone = false;
            boolean mrsDone = false;

            // Labels
            if (labelSync != null) {
                try {
                    SyncResult r = labelSync.syncLabelsForRepository(workspace.getId(), repo);
                    totalLabels += r.count();
                } catch (Exception e) {
                    log.warn("Failed label sync: scopeId={}, repo={}", workspace.getId(), repo.getNameWithOwner(), e);
                }
            }

            // Milestones
            if (milestoneSync != null) {
                try {
                    SyncResult r = milestoneSync.syncMilestonesForRepository(workspace.getId(), repo);
                    totalMilestones += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed milestone sync: scopeId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            // Issues
            if (issueSync != null) {
                try {
                    SyncResult r = issueSync.syncIssues(workspace.getId(), repo, updatedAfter);
                    totalIssues += r.count();
                    issuesDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn("Failed issue sync: scopeId={}, repo={}", workspace.getId(), repo.getNameWithOwner(), e);
                }
            }

            // Merge Requests
            if (mrSync != null) {
                try {
                    SyncResult r = mrSync.syncMergeRequests(workspace.getId(), repo, updatedAfter);
                    totalMRs += r.count();
                    mrsDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn("Failed MR sync: scopeId={}, repo={}", workspace.getId(), repo.getNameWithOwner(), e);
                }
            }

            // Collaborators
            if (collaboratorSync != null) {
                try {
                    SyncResult r = collaboratorSync.syncCollaboratorsForRepository(workspace.getId(), repo);
                    totalCollaborators += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed collaborator sync: scopeId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            // Commits (REST API — uses updatedAfter as since param)
            if (commitSync != null) {
                try {
                    SyncResult r = commitSync.syncCommitsForRepository(workspace.getId(), repo, updatedAfter);
                    totalCommits += r.count();
                } catch (Exception e) {
                    log.warn("Failed commit sync: scopeId={}, repo={}", workspace.getId(), repo.getNameWithOwner(), e);
                }
            }

            // Update lastSyncAt only when all enabled phases completed
            boolean allDone = (issueSync == null || issuesDone) && (mrSync == null || mrsDone);
            if (allDone) {
                repositoryRepository.updateLastSyncAt(repo.getId(), Instant.now());
            }
        }

        log.info(
            "GitLab repo sync complete: scopeId={}, repos={}, labels={}, milestones={}, issues={}, mrs={}, collaborators={}, commits={}",
            workspace.getId(),
            repos.size(),
            totalLabels,
            totalMilestones,
            totalIssues,
            totalMRs,
            totalCollaborators,
            totalCommits
        );
    }

    private void syncPostRepo(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabSubIssueSyncService subIssueSync = services.getSubIssueSyncService();
        GitLabIssueDependencySyncService depSync = services.getIssueDependencySyncService();

        if (subIssueSync == null && depSync == null) return;

        List<Repository> repos = repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
            workspace.getAccountLogin(),
            getGitLabProviderId(workspace)
        );

        int totalSubIssues = 0,
            totalDeps = 0;

        for (Repository repo : repos) {
            if (subIssueSync != null) {
                try {
                    SyncResult r = subIssueSync.syncSubIssuesForRepository(workspace.getId(), repo);
                    totalSubIssues += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed sub-issue sync: scopeId={}, repo={}",
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
                        "Failed dependency sync: scopeId={}, repo={}",
                        workspace.getId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
        }

        if (totalSubIssues > 0 || totalDeps > 0) {
            log.info(
                "GitLab post-repo sync: scopeId={}, subIssues={}, dependencies={}",
                workspace.getId(),
                totalSubIssues,
                totalDeps
            );
        }
    }

    private void syncTeams(GitLabSyncServiceHolder services, Workspace workspace) {
        GitLabTeamSyncService teamSync = services.getTeamSyncService();
        if (teamSync == null) return;

        try {
            int count = teamSync.syncTeamsForGroup(workspace.getId(), workspace.getAccountLogin());
            log.info("GitLab team sync: scopeId={}, teams={}", workspace.getId(), count);
        } catch (Exception e) {
            log.error("Failed GitLab team sync: scopeId={}", workspace.getId(), e);
        }
    }

    /**
     * Resolves the GitLab provider ID for the workspace by looking up an existing
     * repository's provider. Falls back to finding the provider from the organization.
     */
    private Long getGitLabProviderId(Workspace workspace) {
        // Use the organization's provider if available
        if (workspace.getOrganization() != null && workspace.getOrganization().getProvider() != null) {
            return workspace.getOrganization().getProvider().getId();
        }
        // Fallback: query any repo associated with this workspace's org
        return organizationRepository
            .findByLoginIgnoreCase(workspace.getAccountLogin())
            .map(org -> org.getProvider() != null ? org.getProvider().getId() : null)
            .orElse(null);
    }
}

package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.gitlab.GitLabCommitSyncService;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabRateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncSession;
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
 * Uses SPI interfaces to remain decoupled from consuming modules:
 * <ul>
 *   <li>{@link SyncTargetProvider} - provides scope/repository info to sync</li>
 *   <li>{@link SyncContextProvider} - manages context for logging and isolation</li>
 * </ul>
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

    private final SyncTargetProvider syncTargetProvider;
    private final SyncContextProvider syncContextProvider;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider;
    private final ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final Executor monitoringExecutor;

    public GitLabDataSyncScheduler(
        SyncTargetProvider syncTargetProvider,
        SyncContextProvider syncContextProvider,
        OrganizationRepository organizationRepository,
        RepositoryRepository repositoryRepository,
        ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider,
        ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider,
        SyncSchedulerProperties syncSchedulerProperties,
        @Qualifier("monitoringExecutor") Executor monitoringExecutor
    ) {
        this.syncTargetProvider = syncTargetProvider;
        this.syncContextProvider = syncContextProvider;
        this.organizationRepository = organizationRepository;
        this.repositoryRepository = repositoryRepository;
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
        List<SyncSession> sessions = syncTargetProvider.getGitLabSyncSessions();

        if (sessions.isEmpty()) {
            log.debug("No active GitLab workspaces to sync");
            return;
        }

        log.info("Starting scheduled GitLab sync: workspaceCount={}", sessions.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = sessions
            .stream()
            .map(session ->
                CompletableFuture.runAsync(() -> syncScope(session), monitoringExecutor).whenComplete(
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
                sessions.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                "GitLab sync interrupted: workspaceCount={}, successful={}, failed={}",
                sessions.size(),
                successCount.get(),
                failureCount.get()
            );
        } catch (ExecutionException e) {
            log.error("Unexpected error during scheduled GitLab sync", e);
        }
    }

    private void syncScope(SyncSession session) {
        Long scopeId = session.scopeId();
        String safeLogin = sanitizeForLog(session.accountLogin());

        syncContextProvider.setContext(session.syncContext());
        try {
            log.info("Starting GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin);

            GitLabSyncServiceHolder services = syncServiceHolderProvider.getIfAvailable();
            if (services == null) {
                log.warn("GitLab sync services unavailable, skipping: scopeId={}", scopeId);
                return;
            }

            if (session.accountLogin() == null || session.accountLogin().isBlank()) {
                log.warn("No accountLogin for GitLab workspace, skipping: scopeId={}", scopeId);
                return;
            }

            // Phase 1: Sync group projects (discovers new repos, removes deleted ones)
            syncGroupProjects(services, session);

            // Phase 2: Sync group memberships
            syncGroupMembers(services, session);

            // Phase 2.5: Sync issue types (org-level, before per-repo sync)
            syncIssueTypes(services, session);

            // Phase 3: Per-repository sync (labels, milestones, issues, MRs, collaborators)
            syncRepositories(services, session);

            // Phase 4: Post-repo sync (sub-issues, dependencies — needs issues to exist)
            syncPostRepo(services, session);

            // Phase 5: Sync teams (subgroups)
            syncTeams(services, session);

            log.info("Completed GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin);
        } catch (Exception e) {
            log.error("Failed GitLab workspace sync: scopeId={}, accountLogin={}", scopeId, safeLogin, e);
        } finally {
            syncContextProvider.clearContext();
        }
    }

    private void syncGroupProjects(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabGroupSyncService groupSync = services.getGroupSyncService();
        if (groupSync == null) return;

        try {
            GitLabSyncResult result = groupSync.syncGroupProjects(session.scopeId(), session.accountLogin());
            log.info(
                "GitLab group project sync: scopeId={}, status={}, synced={}, pages={}",
                session.scopeId(),
                result.status(),
                result.synced().size(),
                result.pagesCompleted()
            );

            // Stale repo cleanup: only when sync completed normally
            if (result.status() == GitLabSyncResult.Status.COMPLETED) {
                removeStaleRepositories(session, result);
            }
        } catch (Exception e) {
            log.error("Failed GitLab group project sync: scopeId={}", session.scopeId(), e);
        }
    }

    /**
     * Removes repositories that exist in the database but were not found during
     * the latest group project sync. Guards against false positives by only running
     * when the sync completed fully (all pages fetched).
     */
    private void removeStaleRepositories(SyncSession session, GitLabSyncResult result) {
        Long providerId = getGitLabProviderId(session.accountLogin());
        if (providerId == null) return;

        Set<Long> syncedNativeIds = result.synced().stream().map(Repository::getNativeId).collect(Collectors.toSet());

        List<Repository> existingRepos = repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
            session.accountLogin(),
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
            log.info("Removed stale repositories: scopeId={}, count={}", session.scopeId(), removed);
        }
    }

    private void syncGroupMembers(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabGroupMemberSyncService memberSync = services.getGroupMemberSyncService();
        if (memberSync == null) return;

        try {
            organizationRepository
                .findByLoginIgnoreCase(session.accountLogin())
                .ifPresent(org -> {
                    int count = memberSync.syncGroupMemberships(session.scopeId(), session.accountLogin(), org);
                    log.info("GitLab membership sync: scopeId={}, membersSynced={}", session.scopeId(), count);
                });
        } catch (Exception e) {
            log.error("Failed GitLab membership sync: scopeId={}", session.scopeId(), e);
        }
    }

    private void syncIssueTypes(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabIssueTypeSyncService issueTypeSync = services.getIssueTypeSyncService();
        if (issueTypeSync == null) return;

        try {
            int count = issueTypeSync.syncIssueTypesForGroup(session.scopeId(), session.accountLogin());
            log.info("GitLab issue type sync: scopeId={}, types={}", session.scopeId(), count);
        } catch (Exception e) {
            log.error("Failed GitLab issue type sync: scopeId={}", session.scopeId(), e);
        }
    }

    private void syncRepositories(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabLabelSyncService labelSync = services.getLabelSyncService();
        GitLabMilestoneSyncService milestoneSync = services.getMilestoneSyncService();
        GitLabIssueSyncService issueSync = services.getIssueSyncService();
        GitLabMergeRequestSyncService mrSync = services.getMergeRequestSyncService();
        GitLabCollaboratorSyncService collaboratorSync = services.getCollaboratorSyncService();
        GitLabCommitSyncService commitSync = services.getCommitSyncService();

        // Find all repositories monitored by this workspace
        Long providerId = getGitLabProviderId(session.accountLogin());
        List<Repository> repos =
            providerId != null
                ? repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
                      session.accountLogin(),
                      providerId
                  )
                : List.of();

        if (repos.isEmpty()) {
            log.debug("No repositories to sync for GitLab workspace: scopeId={}", session.scopeId());
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
            if (rateLimitTracker != null && rateLimitTracker.isCritical(session.scopeId())) {
                log.info(
                    "Rate limit critical, waiting: scopeId={}, remaining={}",
                    session.scopeId(),
                    rateLimitTracker.getRemaining(session.scopeId())
                );
                try {
                    rateLimitTracker.waitIfNeeded(session.scopeId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Rate limit wait interrupted, stopping repo sync: scopeId={}", session.scopeId());
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
                    SyncResult r = labelSync.syncLabelsForRepository(session.scopeId(), repo);
                    totalLabels += r.count();
                } catch (Exception e) {
                    log.warn("Failed label sync: scopeId={}, repo={}", session.scopeId(), repo.getNameWithOwner(), e);
                }
            }

            // Milestones
            if (milestoneSync != null) {
                try {
                    SyncResult r = milestoneSync.syncMilestonesForRepository(session.scopeId(), repo);
                    totalMilestones += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed milestone sync: scopeId={}, repo={}",
                        session.scopeId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            // Issues
            if (issueSync != null) {
                try {
                    SyncResult r = issueSync.syncIssues(session.scopeId(), repo, updatedAfter);
                    totalIssues += r.count();
                    issuesDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn("Failed issue sync: scopeId={}, repo={}", session.scopeId(), repo.getNameWithOwner(), e);
                }
            }

            // Merge Requests
            if (mrSync != null) {
                try {
                    SyncResult r = mrSync.syncMergeRequests(session.scopeId(), repo, updatedAfter);
                    totalMRs += r.count();
                    mrsDone = r.isCompleted();
                } catch (Exception e) {
                    log.warn("Failed MR sync: scopeId={}, repo={}", session.scopeId(), repo.getNameWithOwner(), e);
                }
            }

            // Collaborators
            if (collaboratorSync != null) {
                try {
                    SyncResult r = collaboratorSync.syncCollaboratorsForRepository(session.scopeId(), repo);
                    totalCollaborators += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed collaborator sync: scopeId={}, repo={}",
                        session.scopeId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }

            // Commits (REST API — uses updatedAfter as since param)
            if (commitSync != null) {
                try {
                    SyncResult r = commitSync.syncCommitsForRepository(session.scopeId(), repo, updatedAfter);
                    totalCommits += r.count();
                } catch (Exception e) {
                    log.warn("Failed commit sync: scopeId={}, repo={}", session.scopeId(), repo.getNameWithOwner(), e);
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
            session.scopeId(),
            repos.size(),
            totalLabels,
            totalMilestones,
            totalIssues,
            totalMRs,
            totalCollaborators,
            totalCommits
        );
    }

    private void syncPostRepo(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabSubIssueSyncService subIssueSync = services.getSubIssueSyncService();
        GitLabIssueDependencySyncService depSync = services.getIssueDependencySyncService();

        if (subIssueSync == null && depSync == null) return;

        Long providerId = getGitLabProviderId(session.accountLogin());
        List<Repository> repos =
            providerId != null
                ? repositoryRepository.findAllByOrganization_LoginIgnoreCaseAndProviderId(
                      session.accountLogin(),
                      providerId
                  )
                : List.of();

        int totalSubIssues = 0,
            totalDeps = 0;

        for (Repository repo : repos) {
            if (subIssueSync != null) {
                try {
                    SyncResult r = subIssueSync.syncSubIssuesForRepository(session.scopeId(), repo);
                    totalSubIssues += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed sub-issue sync: scopeId={}, repo={}",
                        session.scopeId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
            if (depSync != null) {
                try {
                    SyncResult r = depSync.syncDependenciesForRepository(session.scopeId(), repo);
                    totalDeps += r.count();
                } catch (Exception e) {
                    log.warn(
                        "Failed dependency sync: scopeId={}, repo={}",
                        session.scopeId(),
                        repo.getNameWithOwner(),
                        e
                    );
                }
            }
        }

        if (totalSubIssues > 0 || totalDeps > 0) {
            log.info(
                "GitLab post-repo sync: scopeId={}, subIssues={}, dependencies={}",
                session.scopeId(),
                totalSubIssues,
                totalDeps
            );
        }
    }

    private void syncTeams(GitLabSyncServiceHolder services, SyncSession session) {
        GitLabTeamSyncService teamSync = services.getTeamSyncService();
        if (teamSync == null) return;

        try {
            int count = teamSync.syncTeamsForGroup(session.scopeId(), session.accountLogin());
            log.info("GitLab team sync: scopeId={}, teams={}", session.scopeId(), count);
        } catch (Exception e) {
            log.error("Failed GitLab team sync: scopeId={}", session.scopeId(), e);
        }
    }

    /**
     * Resolves the GitLab provider ID by looking up the organization.
     */
    private Long getGitLabProviderId(String accountLogin) {
        return organizationRepository
            .findByLoginIgnoreCase(accountLogin)
            .map(org -> org.getProvider() != null ? org.getProvider().getId() : null)
            .orElse(null);
    }
}

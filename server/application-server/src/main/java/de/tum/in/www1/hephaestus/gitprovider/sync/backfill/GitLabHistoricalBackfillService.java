package de.tum.in.www1.hephaestus.gitprovider.sync.backfill;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.GitLabMergeRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceScopeFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historical backfill service for GitLab repositories.
 * <p>
 * Mirrors the GitHub {@link HistoricalBackfillService} but uses GitLab-specific
 * sync services with {@code CREATED_DESC} ordering to fetch historical data
 * that predates the initial incremental sync window.
 * <p>
 * Backfill state is tracked on {@link RepositoryToMonitor} entities using the same
 * fields as GitHub backfill (highWaterMark, checkpoint, backfillLastRunAt).
 * <p>
 * Each cycle processes one batch per repository, respecting cooldowns between runs.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabHistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(GitLabHistoricalBackfillService.class);
    private static final Duration COOLDOWN_NORMAL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_ERROR = Duration.ofMinutes(15);

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryToMonitorRepository rtmRepository;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Per-repository cooldown tracking for error backoff
    private final Map<Long, Instant> repositoryCooldowns = new ConcurrentHashMap<>();

    public GitLabHistoricalBackfillService(
        WorkspaceRepository workspaceRepository,
        RepositoryRepository repositoryRepository,
        RepositoryToMonitorRepository rtmRepository,
        WorkspaceScopeFilter workspaceScopeFilter,
        ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider,
        SyncSchedulerProperties syncSchedulerProperties
    ) {
        this.workspaceRepository = workspaceRepository;
        this.repositoryRepository = repositoryRepository;
        this.rtmRepository = rtmRepository;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.syncServiceHolderProvider = syncServiceHolderProvider;
        this.syncSchedulerProperties = syncSchedulerProperties;
    }

    /**
     * Runs one backfill cycle across all active GitLab workspaces.
     * Processes one batch per repository that needs backfill.
     *
     * @return number of repositories processed
     */
    public int runBackfillCycle() {
        GitLabSyncServiceHolder services = syncServiceHolderProvider.getIfAvailable();
        if (services == null) return 0;

        GitLabIssueSyncService issueSync = services.getIssueSyncService();
        GitLabMergeRequestSyncService mrSync = services.getMergeRequestSyncService();
        if (issueSync == null && mrSync == null) return 0;

        List<Workspace> gitLabWorkspaces = workspaceRepository
            .findAll()
            .stream()
            .filter(ws -> ws.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .filter(ws -> ws.getProviderType() == GitProviderType.GITLAB)
            .filter(workspaceScopeFilter::isWorkspaceAllowed)
            .toList();

        if (gitLabWorkspaces.isEmpty()) return 0;

        int batchSize = syncSchedulerProperties.backfill().batchSize();
        AtomicInteger processed = new AtomicInteger(0);

        for (Workspace workspace : gitLabWorkspaces) {
            List<RepositoryToMonitor> monitors = rtmRepository.findByWorkspaceId(workspace.getId());

            for (RepositoryToMonitor rtm : monitors) {
                if (rtm.isBackfillComplete()) continue;
                if (isOnCooldown(rtm.getId())) continue;

                // Only backfill repos that have completed initial sync
                if (rtm.getIssuesSyncedAt() == null && rtm.getPullRequestsSyncedAt() == null) {
                    continue;
                }

                Long providerId =
                    workspace.getOrganization() != null && workspace.getOrganization().getProvider() != null
                        ? workspace.getOrganization().getProvider().getId()
                        : null;

                Optional<Repository> repoOpt =
                    providerId != null
                        ? repositoryRepository.findByNameWithOwnerAndProviderId(rtm.getNameWithOwner(), providerId)
                        : repositoryRepository.findByNameWithOwner(rtm.getNameWithOwner());

                if (repoOpt.isEmpty()) continue;
                Repository repo = repoOpt.get();

                boolean worked = backfillRepository(workspace.getId(), repo, rtm, issueSync, mrSync, batchSize);

                if (worked) {
                    processed.incrementAndGet();
                }
            }
        }

        return processed.get();
    }

    private boolean backfillRepository(
        Long scopeId,
        Repository repo,
        RepositoryToMonitor rtm,
        GitLabIssueSyncService issueSync,
        GitLabMergeRequestSyncService mrSync,
        int batchSize
    ) {
        String safeName = sanitizeForLog(repo.getNameWithOwner());
        boolean didWork = false;

        // Backfill issues
        if (issueSync != null && !rtm.isIssueBackfillComplete()) {
            try {
                BackfillBatchResult result = issueSync.backfillIssues(
                    scopeId,
                    repo,
                    rtm.getIssueSyncCursor(),
                    batchSize
                );

                if (result.aborted()) {
                    repositoryCooldowns.put(rtm.getId(), Instant.now().plus(COOLDOWN_ERROR));
                    return false;
                }

                if (result.count() > 0) {
                    didWork = true;
                    updateIssueBackfillState(rtm, result);
                }

                if (result.complete() && result.count() == 0 && !rtm.isIssueBackfillInitialized()) {
                    // No issues at all — mark as complete
                    rtm.setIssueBackfillHighWaterMark(0);
                    rtm.setIssueBackfillCheckpoint(0);
                    rtmRepository.save(rtm);
                }
            } catch (Exception e) {
                log.warn("Issue backfill failed: repo={}", safeName, e);
                repositoryCooldowns.put(rtm.getId(), Instant.now().plus(COOLDOWN_ERROR));
                return didWork;
            }
        }

        // Backfill merge requests
        if (mrSync != null && !rtm.isPullRequestBackfillComplete()) {
            try {
                BackfillBatchResult result = mrSync.backfillMergeRequests(
                    scopeId,
                    repo,
                    rtm.getPullRequestSyncCursor(),
                    batchSize
                );

                if (result.aborted()) {
                    repositoryCooldowns.put(rtm.getId(), Instant.now().plus(COOLDOWN_ERROR));
                    return didWork;
                }

                if (result.count() > 0) {
                    didWork = true;
                    updateMrBackfillState(rtm, result);
                }

                if (result.complete() && result.count() == 0 && !rtm.isPullRequestBackfillInitialized()) {
                    rtm.setPullRequestBackfillHighWaterMark(0);
                    rtm.setPullRequestBackfillCheckpoint(0);
                    rtmRepository.save(rtm);
                }
            } catch (Exception e) {
                log.warn("MR backfill failed: repo={}", safeName, e);
                repositoryCooldowns.put(rtm.getId(), Instant.now().plus(COOLDOWN_ERROR));
                return didWork;
            }
        }

        if (didWork) {
            rtm.setBackfillLastRunAt(Instant.now());
            rtmRepository.save(rtm);
            repositoryCooldowns.put(rtm.getId(), Instant.now().plus(COOLDOWN_NORMAL));
        }

        return didWork;
    }

    @Transactional
    void updateIssueBackfillState(RepositoryToMonitor rtm, BackfillBatchResult result) {
        // Initialize high water mark on first batch
        if (!rtm.isIssueBackfillInitialized() && result.maxIid() > 0) {
            rtm.setIssueBackfillHighWaterMark(result.maxIid());
        }

        // Update checkpoint (lowest IID seen — backfill counts down)
        if (result.minIid() > 0) {
            rtm.setIssueBackfillCheckpoint(result.minIid());
        }

        // Save cursor for pagination resumption
        rtm.setIssueSyncCursor(result.nextCursor());

        // If complete and checkpoint reached 1 or below, mark done
        if (result.complete() && result.nextCursor() == null) {
            rtm.setIssueBackfillCheckpoint(0);
            rtm.setIssueSyncCursor(null);
        }

        rtmRepository.save(rtm);
    }

    @Transactional
    void updateMrBackfillState(RepositoryToMonitor rtm, BackfillBatchResult result) {
        if (!rtm.isPullRequestBackfillInitialized() && result.maxIid() > 0) {
            rtm.setPullRequestBackfillHighWaterMark(result.maxIid());
        }

        if (result.minIid() > 0) {
            rtm.setPullRequestBackfillCheckpoint(result.minIid());
        }

        rtm.setPullRequestSyncCursor(result.nextCursor());

        if (result.complete() && result.nextCursor() == null) {
            rtm.setPullRequestBackfillCheckpoint(0);
            rtm.setPullRequestSyncCursor(null);
        }

        rtmRepository.save(rtm);
    }

    private boolean isOnCooldown(Long rtmId) {
        Instant cooldownUntil = repositoryCooldowns.get(rtmId);
        return cooldownUntil != null && Instant.now().isBefore(cooldownUntil);
    }
}

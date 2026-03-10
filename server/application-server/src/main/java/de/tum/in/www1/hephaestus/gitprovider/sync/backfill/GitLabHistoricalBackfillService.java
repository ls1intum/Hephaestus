package de.tum.in.www1.hephaestus.gitprovider.sync.backfill;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncServiceHolder;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncSession;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.GitLabIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.GitLabMergeRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
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

/**
 * Historical backfill service for GitLab repositories.
 * <p>
 * Mirrors the GitHub {@link HistoricalBackfillService} but uses GitLab-specific
 * sync services with {@code CREATED_DESC} ordering to fetch historical data
 * that predates the initial incremental sync window.
 * <p>
 * Backfill state is tracked via the {@link BackfillStateProvider} SPI using the same
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

    private final SyncTargetProvider syncTargetProvider;
    private final RepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Per-repository cooldown tracking for error backoff
    private final Map<Long, Instant> repositoryCooldowns = new ConcurrentHashMap<>();

    public GitLabHistoricalBackfillService(
        SyncTargetProvider syncTargetProvider,
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository,
        ObjectProvider<GitLabSyncServiceHolder> syncServiceHolderProvider,
        SyncSchedulerProperties syncSchedulerProperties
    ) {
        this.syncTargetProvider = syncTargetProvider;
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
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

        List<SyncSession> sessions = syncTargetProvider.getGitLabSyncSessions();
        if (sessions.isEmpty()) return 0;

        int batchSize = syncSchedulerProperties.backfill().batchSize();
        AtomicInteger processed = new AtomicInteger(0);

        for (SyncSession session : sessions) {
            Long providerId = getGitLabProviderId(session.accountLogin());

            for (SyncTarget target : session.syncTargets()) {
                if (target.isBackfillComplete()) continue;
                if (isOnCooldown(target.id())) continue;

                // Only backfill repos that have completed initial sync
                if (target.lastIssuesSyncedAt() == null && target.lastPullRequestsSyncedAt() == null) {
                    continue;
                }

                Optional<Repository> repoOpt =
                    providerId != null
                        ? repositoryRepository.findByNameWithOwnerAndProviderId(
                              target.repositoryNameWithOwner(),
                              providerId
                          )
                        : repositoryRepository.findByNameWithOwner(target.repositoryNameWithOwner());

                if (repoOpt.isEmpty()) continue;
                Repository repo = repoOpt.get();

                boolean worked = backfillRepository(session.scopeId(), repo, target, issueSync, mrSync, batchSize);

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
        SyncTarget target,
        GitLabIssueSyncService issueSync,
        GitLabMergeRequestSyncService mrSync,
        int batchSize
    ) {
        String safeName = sanitizeForLog(repo.getNameWithOwner());
        boolean didWork = false;

        // Backfill issues
        if (issueSync != null && !target.isIssueBackfillComplete()) {
            try {
                BackfillBatchResult result = issueSync.backfillIssues(
                    scopeId,
                    repo,
                    target.issueSyncCursor(),
                    batchSize
                );

                if (result.aborted()) {
                    repositoryCooldowns.put(target.id(), Instant.now().plus(COOLDOWN_ERROR));
                    return false;
                }

                if (result.count() > 0) {
                    didWork = true;
                    updateIssueBackfillState(target, result);
                }

                if (result.complete() && result.count() == 0 && !target.isIssueBackfillInitialized()) {
                    // No issues at all — mark as complete
                    syncTargetProvider.updateIssueBackfillState(target.id(), 0, 0, null);
                }
            } catch (Exception e) {
                log.warn("Issue backfill failed: repo={}", safeName, e);
                repositoryCooldowns.put(target.id(), Instant.now().plus(COOLDOWN_ERROR));
                return didWork;
            }
        }

        // Backfill merge requests
        if (mrSync != null && !target.isPullRequestBackfillComplete()) {
            try {
                BackfillBatchResult result = mrSync.backfillMergeRequests(
                    scopeId,
                    repo,
                    target.pullRequestSyncCursor(),
                    batchSize
                );

                if (result.aborted()) {
                    repositoryCooldowns.put(target.id(), Instant.now().plus(COOLDOWN_ERROR));
                    return didWork;
                }

                if (result.count() > 0) {
                    didWork = true;
                    updateMrBackfillState(target, result);
                }

                if (result.complete() && result.count() == 0 && !target.isPullRequestBackfillInitialized()) {
                    syncTargetProvider.updatePullRequestBackfillState(target.id(), 0, 0, null);
                }
            } catch (Exception e) {
                log.warn("MR backfill failed: repo={}", safeName, e);
                repositoryCooldowns.put(target.id(), Instant.now().plus(COOLDOWN_ERROR));
                return didWork;
            }
        }

        if (didWork) {
            syncTargetProvider.updateIssueBackfillState(target.id(), null, null, Instant.now());
            repositoryCooldowns.put(target.id(), Instant.now().plus(COOLDOWN_NORMAL));
        }

        return didWork;
    }

    private void updateIssueBackfillState(SyncTarget target, BackfillBatchResult result) {
        // Initialize high water mark on first batch
        Integer highWaterMark = null;
        if (!target.isIssueBackfillInitialized() && result.maxIid() > 0) {
            highWaterMark = result.maxIid();
        }

        // Update checkpoint (lowest IID seen — backfill counts down)
        Integer checkpoint = result.minIid() > 0 ? result.minIid() : null;

        // If complete and no more pages, mark done
        if (result.complete() && result.nextCursor() == null) {
            checkpoint = 0;
        }

        syncTargetProvider.updateIssueBackfillState(target.id(), highWaterMark, checkpoint, null);

        // Save cursor for pagination resumption
        String cursor = (result.complete() && result.nextCursor() == null) ? null : result.nextCursor();
        syncTargetProvider.updateIssueSyncCursor(target.id(), cursor);
    }

    private void updateMrBackfillState(SyncTarget target, BackfillBatchResult result) {
        Integer highWaterMark = null;
        if (!target.isPullRequestBackfillInitialized() && result.maxIid() > 0) {
            highWaterMark = result.maxIid();
        }

        Integer checkpoint = result.minIid() > 0 ? result.minIid() : null;

        if (result.complete() && result.nextCursor() == null) {
            checkpoint = 0;
        }

        syncTargetProvider.updatePullRequestBackfillState(target.id(), highWaterMark, checkpoint, null);

        String cursor = (result.complete() && result.nextCursor() == null) ? null : result.nextCursor();
        syncTargetProvider.updatePullRequestSyncCursor(target.id(), cursor);
    }

    private boolean isOnCooldown(Long syncTargetId) {
        Instant cooldownUntil = repositoryCooldowns.get(syncTargetId);
        return cooldownUntil != null && Instant.now().isBefore(cooldownUntil);
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

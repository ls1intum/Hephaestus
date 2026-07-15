package de.tum.cit.aet.hephaestus.integration.scm.github.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.BackfillSummary;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ScopedRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.backfill.GitHubHistoricalBackfillService.BackfillProgress;
import de.tum.cit.aet.hephaestus.integration.scm.github.workspace.GitHubInstallationSuspensionTracker;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * GitHub {@link ConnectionSyncStateProvider}: connection-level sync snapshot and per-repository
 * resource rows for the sync-observability read model.
 *
 * <p>Both {@link #describe} and {@link #resources} are O(DB + in-memory) only — no live GitHub API
 * call — per the SPI contract: the overview page renders every connected integration on one load.
 */
@Component
public class GithubConnectionSyncStateProvider implements ConnectionSyncStateProvider {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionSyncStateProvider.class);

    private final ConnectionRepository connectionRepository;
    private final ScopedRateLimitTracker rateLimitTracker;
    private final GitHubInstallationSuspensionTracker suspensionTracker;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;

    public GithubConnectionSyncStateProvider(
        ConnectionRepository connectionRepository,
        ScopedRateLimitTracker rateLimitTracker,
        GitHubInstallationSuspensionTracker suspensionTracker,
        SyncSchedulerProperties syncSchedulerProperties,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        IssueRepository issueRepository
    ) {
        this.connectionRepository = connectionRepository;
        this.rateLimitTracker = rateLimitTracker;
        this.suspensionTracker = suspensionTracker;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.issueRepository = issueRepository;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public ConnectionSyncDetails describe(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        Connection connection = connectionRepository.findById(connectionId).orElse(null);

        // App-installation workspaces have a vendor-managed webhook (registered at install time), so we
        // report TRUE. PAT connections have no webhook signal we track, so webhookRegistered stays null
        // (the @Nullable "not applicable / unknown" value) rather than a claimed FALSE.
        Boolean webhookRegistered = null;
        Long installationId = null;
        if (connection != null && connection.getConfig() instanceof ConnectionConfig.GitHubAppConfig appConfig) {
            webhookRegistered = Boolean.TRUE;
            installationId = appConfig.installationId();
        }

        boolean vendorHealthDegraded = false;
        String degradedReason = null;
        if (installationId != null && suspensionTracker.isInstallationMarkedSuspended(installationId)) {
            vendorHealthDegraded = true;
            degradedReason = "GitHub App installation is suspended";
        }

        return new ConnectionSyncDetails(
            webhookRegistered,
            nextScheduledSyncAt(),
            rateLimitTracker.snapshot(workspaceId),
            aggregateBackfill(workspaceId),
            vendorHealthDegraded,
            degradedReason
        );
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspaceId);
        if (monitors.isEmpty()) {
            return List.of();
        }

        // Batch-resolve nameWithOwner -> local Repository id (single query), then a single grouped
        // issue+PR count query — avoids an N+1 per monitored repository.
        Map<String, Long> repositoryIdByName = repositoryRepository
            .findAllByWorkspaceMonitors(workspaceId)
            .stream()
            .collect(Collectors.toMap(Repository::getNameWithOwner, Repository::getId, (a, b) -> a));

        Map<Long, Long> itemCountByRepositoryId = repositoryIdByName.isEmpty()
            ? Map.of()
            : issueRepository
                  .countGroupedByRepositoryIds(repositoryIdByName.values())
                  .stream()
                  .collect(
                      Collectors.toMap(
                          IssueRepository.RepositoryItemCount::getRepositoryId,
                          IssueRepository.RepositoryItemCount::getItemCount
                      )
                  );

        return monitors
            .stream()
            .map(rtm -> toResourceState(rtm, repositoryIdByName, itemCountByRepositoryId))
            .toList();
    }

    private SyncResourceState toResourceState(
        RepositoryToMonitor rtm,
        Map<String, Long> repositoryIdByName,
        Map<Long, Long> itemCountByRepositoryId
    ) {
        Long repositoryId = repositoryIdByName.get(rtm.getNameWithOwner());
        Long itemCount = repositoryId == null ? null : itemCountByRepositoryId.get(repositoryId);

        Instant lastSyncedAt = latestNonNull(
            rtm.getIssuesSyncedAt(),
            rtm.getPullRequestsSyncedAt(),
            rtm.getRepositorySyncedAt()
        );
        String state =
            rtm.getIssuesSyncedAt() == null || rtm.getPullRequestsSyncedAt() == null
                ? SyncResourceState.STATE_PENDING
                : SyncResourceState.STATE_SYNCED;

        int backfillTotal =
            (rtm.getIssueBackfillHighWaterMark() != null ? rtm.getIssueBackfillHighWaterMark() : 0) +
            (rtm.getPullRequestBackfillHighWaterMark() != null ? rtm.getPullRequestBackfillHighWaterMark() : 0);
        Integer backfillPercent = BackfillProgress.percentComplete(
            rtm.isBackfillInitialized(),
            rtm.getBackfillRemaining(),
            backfillTotal
        );

        return new SyncResourceState(
            rtm.getId(),
            rtm.getNameWithOwner(),
            rtm.getNameWithOwner(),
            SyncResourceState.Type.REPOSITORY,
            state,
            lastSyncedAt,
            itemCount,
            // upstreamCount: would require a live vendor call — the SPI forbids that in resources().
            null,
            // Per-resource errors are not persisted yet.
            null,
            // backfillCompletedThrough: GitHub's backfill horizon is issue/PR-NUMBER based
            // (highWaterMark/checkpoint), not date-based — no per-item timestamp to report here
            // without an extra vendor call.
            null,
            backfillPercent
        );
    }

    /**
     * Connection-level backfill rollup, computed from a single {@code findByWorkspaceId}
     * load using the same high-water-mark / remaining / initialized fields {@code toResourceState} reads per
     * resource — so no per-target {@code getProgress} re-fetch (an N+1 over the monitor set) is needed.
     */
    private BackfillSummary aggregateBackfill(long workspaceId) {
        if (!syncSchedulerProperties.backfill().enabled()) {
            return new BackfillSummary("DISABLED", null, null);
        }
        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspaceId);
        if (monitors.isEmpty()) {
            return new BackfillSummary("NOT_STARTED", null, null);
        }

        boolean anyInitialized = false;
        boolean allComplete = true;
        long totalItems = 0;
        long doneItems = 0;
        for (RepositoryToMonitor rtm : monitors) {
            anyInitialized = anyInitialized || rtm.isBackfillInitialized();
            allComplete = allComplete && rtm.isBackfillComplete();
            int highWaterMark =
                (rtm.getIssueBackfillHighWaterMark() != null ? rtm.getIssueBackfillHighWaterMark() : 0) +
                (rtm.getPullRequestBackfillHighWaterMark() != null ? rtm.getPullRequestBackfillHighWaterMark() : 0);
            totalItems += highWaterMark;
            doneItems += Math.max(0, highWaterMark - rtm.getBackfillRemaining());
        }

        String state = allComplete ? "COMPLETE" : (anyInitialized ? "IN_PROGRESS" : "NOT_STARTED");
        Integer percent =
            totalItems > 0 ? (int) Math.round((100.0 * doneItems) / totalItems) : (anyInitialized ? 100 : null);

        // See toResourceState: no per-item timestamp exists for a number-based backfill horizon.
        return new BackfillSummary(state, null, percent);
    }

    /**
     * Computes the next daily-cron reconciliation run from {@code hephaestus.sync.cron}, in the
     * server's default zone (matching {@code @Scheduled(cron=...)}'s un-zoned behavior).
     */
    @Nullable
    private Instant nextScheduledSyncAt() {
        try {
            CronExpression cronExpression = CronExpression.parse(syncSchedulerProperties.cron());
            LocalDateTime next = cronExpression.next(LocalDateTime.now());
            return next == null ? null : next.atZone(ZoneId.systemDefault()).toInstant();
        } catch (IllegalArgumentException e) {
            log.warn(
                "Failed to parse sync cron expression for nextScheduledSyncAt: cron={}, error={}",
                syncSchedulerProperties.cron(),
                e.getMessage()
            );
            return null;
        }
    }

    @Nullable
    private static Instant latestNonNull(Instant... candidates) {
        Instant latest = null;
        for (Instant candidate : candidates) {
            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest;
    }
}

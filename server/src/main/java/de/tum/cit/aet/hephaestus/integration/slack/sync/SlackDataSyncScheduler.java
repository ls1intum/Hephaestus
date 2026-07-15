package de.tum.cit.aet.hephaestus.integration.slack.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly Slack reconciliation, the Slack counterpart of the SCM data-sync schedulers: refresh channel metadata
 * (names, archived/kicked detection), then replay {@code conversations.history} for ACTIVE channels through the
 * consent-gated ingest stack ({@link SlackChannelHistorySyncService}). Workspaces run sequentially — unlike the SCM
 * sync there is no per-workspace fan-out benefit, because the clamped history methods are paced to ~1 request/minute
 * per workspace token anyway and the whole run is bounded by per-workspace request budgets.
 *
 * <p>{@link WorkspaceAgnostic} because the workspace enumeration is inherently fleet-wide (unscoped native query);
 * everything after enumeration is workspace-scoped. Locked cross-pod like the retention sweeper; gated to the server
 * role so the webhook container never runs it.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Fleet-wide nightly Slack reconciliation; enumerates workspaces before scoping to each")
public class SlackDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlackDataSyncScheduler.class);

    private final SlackMonitoredChannelRepository monitoredChannelRepository;
    private final SlackChannelMetadataRefresher metadataRefresher;
    private final SlackChannelHistorySyncService historySyncService;
    private final ConnectionService connectionService;
    private final SyncJobService syncJobService;
    private final SlackSyncProperties properties;

    public SlackDataSyncScheduler(
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackChannelMetadataRefresher metadataRefresher,
        SlackChannelHistorySyncService historySyncService,
        ConnectionService connectionService,
        SyncJobService syncJobService,
        SlackSyncProperties properties
    ) {
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.metadataRefresher = metadataRefresher;
        this.historySyncService = historySyncService;
        this.connectionService = connectionService;
        this.syncJobService = syncJobService;
        this.properties = properties;
    }

    @Scheduled(cron = "${hephaestus.sync.slack.cron}")
    @SchedulerLock(name = "slack-data-sync", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1M")
    public void syncDataCron() {
        List<Long> workspaceIds = monitoredChannelRepository.findDistinctWorkspaceIdsByConsentState("ACTIVE");
        if (workspaceIds.isEmpty()) {
            return;
        }
        log.info("slack.sync: starting nightly reconciliation for {} workspace(s)", workspaceIds.size());
        int failed = 0;
        for (Long workspaceId : workspaceIds) {
            try {
                syncWorkspaceRecordingJob(workspaceId);
            } catch (SyncJobConflictException e) {
                // Another sync (manual trigger, or a previous tick that hasn't finished) already holds the
                // one-active-job slot for this connection — skip this tick rather than treat it as a failure.
                log.info("slack.sync: workspace {} already has an active sync job, skipping tick", workspaceId);
            } catch (RuntimeException e) {
                failed++;
                log.warn("slack.sync: workspace {} failed: {}", workspaceId, e.toString());
            }
        }
        log.info("slack.sync: nightly reconciliation done: workspaces={}, failed={}", workspaceIds.size(), failed);
    }

    /**
     * Wraps one workspace's nightly reconciliation in the shared {@link SyncJobService} template
     * (design doc §3.4) so it shows up in job history — but only when the workspace has an ACTIVE Slack
     * {@link Connection} to record it against. A workspace whose channels are still {@code ACTIVE} in the
     * allow-list table but whose Connection already left ACTIVE (uninstall race — the normal window
     * {@link #syncWorkspaceNow}'s own guard covers) is skipped here without ever touching Slack,
     * unrecorded, since there is no Connection row to attach a {@code SyncJob} to.
     */
    private void syncWorkspaceRecordingJob(long workspaceId) {
        Optional<Connection> connection = connectionService.findActive(workspaceId, IntegrationKind.SLACK);
        if (connection.isEmpty()) {
            log.debug(
                "slack.sync: workspaceId={} has no ACTIVE Slack connection — skipping tick (unrecorded)",
                workspaceId
            );
            return;
        }
        syncJobService.run(
            new SyncJobRequest(
                workspaceId,
                connection.get().getId(),
                IntegrationKind.SLACK,
                SyncJobType.RECONCILIATION,
                SyncJobTrigger.SCHEDULED,
                null
            ),
            handle -> {
                // The handle is threaded for progress reporting; cancellation is intentionally coarse —
                // the per-channel history loop is private and consent-gated, so a cancel is observed only
                // at the next tick, never mid-run (see SlackIntegrationSyncRunner's cancellation javadoc).
                SlackChannelHistorySyncService.WorkspaceSyncSummary summary = syncWorkspaceNow(workspaceId);
                handle.progress(
                    summary.synced() + summary.skipped(),
                    summary.channels(),
                    Map.of(
                        "channels",
                        summary.channels(),
                        "synced",
                        summary.synced(),
                        "skipped",
                        summary.skipped(),
                        "failed",
                        summary.failed(),
                        "ingested",
                        summary.ingested(),
                        "requestsUsed",
                        summary.requestsUsed(),
                        "budgetExhausted",
                        summary.budgetExhausted()
                    )
                );
            }
        );
    }

    /**
     * Run one workspace's reconciliation immediately (tests and operational replays).
     *
     * <p>Returns an empty summary without touching Slack when the workspace has no ACTIVE Slack connection: every
     * call would fail on token resolution anyway, and attempting them would spend the rate-limit budget (and its
     * pacing sleep) plus log one warning per channel for a workspace that simply is not connected. Monitored
     * channels normally disappear with the connection on uninstall; this covers the window in between.
     */
    public SlackChannelHistorySyncService.WorkspaceSyncSummary syncWorkspaceNow(long workspaceId) {
        if (connectionService.findActive(workspaceId, IntegrationKind.SLACK).isEmpty()) {
            log.debug(
                "slack.sync: workspaceId={} has no ACTIVE Slack connection — skipping reconciliation",
                workspaceId
            );
            return SlackChannelHistorySyncService.WorkspaceSyncSummary.notConnected();
        }
        if (properties.metadataEnabled()) {
            metadataRefresher.refreshWorkspace(workspaceId);
        }
        SlackChannelHistorySyncService.WorkspaceSyncSummary summary = historySyncService.syncWorkspace(workspaceId);
        log.info(
            "slack.sync: workspaceId={} channels={} synced={} skipped={} failed={} ingested={} requests={} budgetExhausted={}",
            workspaceId,
            summary.channels(),
            summary.synced(),
            summary.skipped(),
            summary.failed(),
            summary.ingested(),
            summary.requestsUsed(),
            summary.budgetExhausted()
        );
        return summary;
    }
}

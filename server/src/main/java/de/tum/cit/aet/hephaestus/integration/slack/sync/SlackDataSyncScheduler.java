package de.tum.cit.aet.hephaestus.integration.slack.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import java.util.List;
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
    private final SlackSyncProperties properties;

    public SlackDataSyncScheduler(
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackChannelMetadataRefresher metadataRefresher,
        SlackChannelHistorySyncService historySyncService,
        ConnectionService connectionService,
        SlackSyncProperties properties
    ) {
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.metadataRefresher = metadataRefresher;
        this.historySyncService = historySyncService;
        this.connectionService = connectionService;
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
                syncWorkspaceNow(workspaceId);
            } catch (RuntimeException e) {
                failed++;
                log.warn("slack.sync: workspace {} failed: {}", workspaceId, e.toString());
            }
        }
        log.info("slack.sync: nightly reconciliation done: workspaces={}, failed={}", workspaceIds.size(), failed);
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
            "slack.sync: workspaceId={} channels={} synced={} skipped={} ingested={} requests={} budgetExhausted={}",
            workspaceId,
            summary.channels(),
            summary.synced(),
            summary.skipped(),
            summary.ingested(),
            summary.requestsUsed(),
            summary.budgetExhausted()
        );
        return summary;
    }
}

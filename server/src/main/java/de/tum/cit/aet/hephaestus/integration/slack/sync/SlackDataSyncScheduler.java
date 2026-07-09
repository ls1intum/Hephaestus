package de.tum.cit.aet.hephaestus.integration.slack.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
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
    private final SlackSyncProperties properties;

    public SlackDataSyncScheduler(
        SlackMonitoredChannelRepository monitoredChannelRepository,
        SlackChannelMetadataRefresher metadataRefresher,
        SlackChannelHistorySyncService historySyncService,
        SlackSyncProperties properties
    ) {
        this.monitoredChannelRepository = monitoredChannelRepository;
        this.metadataRefresher = metadataRefresher;
        this.historySyncService = historySyncService;
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

    /** Run one workspace's reconciliation immediately (tests and operational replays). */
    public SlackChannelHistorySyncService.WorkspaceSyncSummary syncWorkspaceNow(long workspaceId) {
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

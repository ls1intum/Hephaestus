package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackChannelHistorySyncService.WorkspaceSyncSummary;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackDataSyncScheduler;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs Slack reconciliation through the consent-gated history sync. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackIntegrationSyncRunner implements IntegrationSyncRunner {

    private final SlackDataSyncScheduler dataSyncScheduler;

    public SlackIntegrationSyncRunner(SlackDataSyncScheduler dataSyncScheduler) {
        this.dataSyncScheduler = dataSyncScheduler;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public void reconcile(IntegrationRef ref, SyncExecutionHandle handle) {
        WorkspaceSyncSummary summary = dataSyncScheduler.syncWorkspaceNow(ref.workspaceId(), handle);
        handle.progress(summary.synced() + summary.skipped(), summary.channels(), progressDetail(summary));
        if (summary.failed() > 0 || summary.budgetExhausted()) {
            handle.reportWarnings();
        }
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }

    static Map<String, Object> progressDetail(WorkspaceSyncSummary summary) {
        return Map.of(
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
        );
    }
}

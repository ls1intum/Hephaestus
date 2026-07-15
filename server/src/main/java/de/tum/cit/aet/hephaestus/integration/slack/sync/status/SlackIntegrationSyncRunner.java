package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackChannelHistorySyncService.WorkspaceSyncSummary;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackDataSyncScheduler;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slack's {@link IntegrationSyncRunner}: the manual-trigger and (via {@link SlackDataSyncScheduler}) the
 * nightly-cron body, both funneled through {@link SlackDataSyncScheduler#syncWorkspaceNow} — the same
 * consent-gated-per-page entry point the scheduler already uses (design doc §3.4: "no extraction
 * needed").
 *
 * <p><strong>Outcome mapping (design doc §3.1, "Outcome mapping v1"):</strong> when the
 * {@link WorkspaceSyncSummary} reports at least one genuinely failed channel
 * ({@link WorkspaceSyncSummary#failed()} &gt; 0 — a channel whose history sync threw, distinct from a
 * benign nothing-to-sync/budget skip), the runner calls {@link SyncJobHandle#reportWarnings()} so the
 * job finalizes {@code SUCCEEDED_WITH_WARNINGS}; the per-channel shortfall is also surfaced in the
 * job row's progress detail. A summary with only benign skips completes plain {@code SUCCEEDED}. Total
 * failures propagate naturally: an exception thrown before {@code syncWorkspaceNow} produces a summary
 * (e.g. channel-metadata refresh, or the monitored-channel query itself) is not caught here and is left
 * to fail the job as {@code FAILED} with the exception's message as the error summary.
 *
 * <p><strong>Cancellation is coarse-grained.</strong> {@code syncWorkspaceNow} delegates to
 * {@code SlackChannelHistorySyncService.syncWorkspace}, whose per-channel loop is private and not threaded
 * with a cancellation check (design doc §3.4 deliberately avoids extracting/refactoring that method — it
 * already carries consent-gating invariants that must not be disturbed). A cooperative cancel request is
 * therefore only observed at the NEXT manual trigger or nightly tick, never mid-run; the whole reconcile
 * pass (metadata refresh + the full per-channel history/replies loop, paced at Slack's ~1 request/minute
 * budget) runs to completion once started.
 */
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
    public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
        WorkspaceSyncSummary summary = dataSyncScheduler.syncWorkspaceNow(ref.workspaceId());
        handle.progress(summary.synced() + summary.skipped(), summary.channels(), progressDetail(summary));
        if (summary.failed() > 0) {
            // At least one channel's history sync threw — a genuine partial failure, not a benign skip.
            handle.reportWarnings();
        }
    }

    @Override
    public boolean supportsBackfill() {
        return false;
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

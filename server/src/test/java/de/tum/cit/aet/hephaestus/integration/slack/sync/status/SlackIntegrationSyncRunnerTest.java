package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackChannelHistorySyncService.WorkspaceSyncSummary;
import de.tum.cit.aet.hephaestus.integration.slack.sync.SlackDataSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/** The runner's dispatch to the existing scheduler entry point and its progress-mapping of the summary. */
@Tag("unit")
class SlackIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WS = 55L;
    private static final IntegrationRef REF = new IntegrationRef(IntegrationKind.SLACK, WS, "T999");

    @Mock
    private SlackDataSyncScheduler dataSyncScheduler;

    @Mock
    private SyncJobHandle handle;

    private SlackIntegrationSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new SlackIntegrationSyncRunner(dataSyncScheduler);
    }

    @Test
    void reconcile_delegatesToSyncWorkspaceNowAndReportsProgressFromTheSummary() {
        // No failed channels -> plain SUCCEEDED (no warnings).
        WorkspaceSyncSummary summary = new WorkspaceSyncSummary(3, 2, 1, 7L, 4, false, 0);
        when(dataSyncScheduler.syncWorkspaceNow(WS, handle)).thenReturn(summary);

        runner.reconcile(REF, handle);

        verify(dataSyncScheduler).syncWorkspaceNow(WS, handle);
        // itemsProcessed = synced + skipped (every channel the loop finished considering, whether or not it
        // ingested anything); itemsTotal = channels (the coarse "N of M channels" progress bar denominator).
        // Detail content is asserted in progressDetail_carriesEveryFieldOfTheSummary — here just the counts.
        verify(handle).progress(eq(3), eq(3), anyMap());
        verify(handle, never()).reportWarnings();
    }

    @Test
    void reconcile_reportsWarningsWhenTheSummaryHasFailedChannels() {
        // One channel's history sync threw (failed=1) — a genuine partial failure, so the job must finalize
        // SUCCEEDED_WITH_WARNINGS rather than a bare SUCCEEDED.
        WorkspaceSyncSummary partial = new WorkspaceSyncSummary(4, 1, 2, 2L, 4, true, 1);
        when(dataSyncScheduler.syncWorkspaceNow(WS, handle)).thenReturn(partial);

        runner.reconcile(REF, handle);

        verify(handle).progress(eq(3), eq(4), anyMap());
        verify(handle).reportWarnings();
    }

    @Test
    void reconcile_reportsWarningsWhenTheRequestBudgetIsExhausted() {
        WorkspaceSyncSummary partial = new WorkspaceSyncSummary(3, 1, 2, 2L, 4, true, 0);
        when(dataSyncScheduler.syncWorkspaceNow(WS, handle)).thenReturn(partial);

        runner.reconcile(REF, handle);

        verify(handle).reportWarnings();
    }

    @Test
    void reconcile_reportsCancellationObservedByTheSync() {
        WorkspaceSyncSummary summary = new WorkspaceSyncSummary(3, 1, 2, 1L, 1, false, 0);
        when(dataSyncScheduler.syncWorkspaceNow(WS, handle)).thenReturn(summary);
        when(handle.isCancellationRequested()).thenReturn(true);

        runner.reconcile(REF, handle);

        verify(handle).reportCancelled();
    }

    @Test
    void progressDetail_carriesEveryFieldOfTheSummary() {
        WorkspaceSyncSummary summary = new WorkspaceSyncSummary(3, 2, 1, 7L, 4, true, 1);

        Map<String, Object> detail = SlackIntegrationSyncRunner.progressDetail(summary);

        assertThat(detail)
            .containsEntry("channels", 3)
            .containsEntry("synced", 2)
            .containsEntry("skipped", 1)
            .containsEntry("failed", 1)
            .containsEntry("ingested", 7L)
            .containsEntry("requestsUsed", 4)
            .containsEntry("budgetExhausted", true);
    }
}

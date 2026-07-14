package de.tum.cit.aet.hephaestus.integration.slack.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
    void kind_isSlack() {
        assertThat(runner.kind()).isEqualTo(IntegrationKind.SLACK);
    }

    @Test
    void supportsBackfill_isFalse() {
        assertThat(runner.supportsBackfill()).isFalse();
    }

    @Test
    void reconcile_delegatesToSyncWorkspaceNowAndReportsProgressFromTheSummary() {
        WorkspaceSyncSummary summary = new WorkspaceSyncSummary(3, 2, 1, 7L, 4, true);
        when(dataSyncScheduler.syncWorkspaceNow(WS)).thenReturn(summary);

        runner.reconcile(REF, handle);

        verify(dataSyncScheduler).syncWorkspaceNow(WS);
        // itemsProcessed = synced + skipped (every channel the loop finished considering, whether or not it
        // ingested anything); itemsTotal = channels (the coarse "N of M channels" progress bar denominator).
        verify(handle).progress(eq(3), eq(3), eq(SlackIntegrationSyncRunner.progressDetail(summary)));
    }

    @Test
    void reconcile_partialFailureIsVisibleOnlyInProgressDetail_neverThrows() {
        // Design doc §3.1 "Outcome mapping v1": the template has no non-throwing hook to elevate a job to
        // SUCCEEDED_WITH_WARNINGS, so a summary with skipped channels must not throw — it completes SUCCEEDED
        // with the shortfall visible in the progress detail (accepted v1 fidelity gap).
        WorkspaceSyncSummary partial = new WorkspaceSyncSummary(4, 1, 3, 2L, 4, true);
        when(dataSyncScheduler.syncWorkspaceNow(WS)).thenReturn(partial);

        runner.reconcile(REF, handle);

        verify(handle).progress(eq(4), eq(4), eq(SlackIntegrationSyncRunner.progressDetail(partial)));
    }

    @Test
    void progressDetail_carriesEveryFieldOfTheSummary() {
        WorkspaceSyncSummary summary = new WorkspaceSyncSummary(3, 2, 1, 7L, 4, true);

        Map<String, Object> detail = SlackIntegrationSyncRunner.progressDetail(summary);

        assertThat(detail)
            .containsEntry("channels", 3)
            .containsEntry("synced", 2)
            .containsEntry("skipped", 1)
            .containsEntry("ingested", 7L)
            .containsEntry("requestsUsed", 4)
            .containsEntry("budgetExhausted", true);
    }
}

package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.GitlabDataSyncScheduler;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.backfill.GitLabHistoricalBackfillService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
class GitlabIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private GitlabDataSyncScheduler dataSyncScheduler;

    @Mock
    private GitLabHistoricalBackfillService backfillService;

    @Mock
    private SyncJobHandle handle;

    private GitlabIntegrationSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new GitlabIntegrationSyncRunner(dataSyncScheduler, backfillService);
    }

    @Test
    void reconcile_reportsCancelledWhenAborted() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");
        when(handle.isCancellationRequested()).thenReturn(true);

        runner.reconcile(ref, handle, SyncJobType.RECONCILIATION);

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle, SyncJobType.RECONCILIATION);
        verify(handle).reportCancelled();
    }

    @Test
    void reconcile_completedWithoutCancellation_doesNotReportCancelled() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null);
        when(handle.isCancellationRequested()).thenReturn(false);

        runner.reconcile(ref, handle, SyncJobType.RECONCILIATION);

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle, SyncJobType.RECONCILIATION);
        verify(handle, never()).reportCancelled();
    }

    @Nested
    class Backfill {

        private final IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");

        @Test
        void supportsBackfill_isAlwaysOfferedIndependentlyOfTheScheduledCycleFlag() {
            // The flag gates the scheduled cycle, not the capability: an administratively paused cycle
            // must still be resumable by hand, which is what runBackfillPass honors.
            assertThat(runner.supportsBackfill()).isTrue();
        }

        /**
         * The asymmetry this closes: GitHub's "Run backfill" drives to completion while GitLab's ran a
         * single batch per click, behind identical-looking buttons. The runner now loops until a pass
         * advances nothing, exactly like {@code GithubIntegrationSyncRunner#backfill}.
         */
        @Test
        void keepsRunningPassesWhileRepositoriesAdvance_andStopsOnTheFirstUnproductiveOne() {
            when(backfillService.runBackfillPass(eq(WORKSPACE_ID), eq(handle))).thenReturn(2, 1, 0);

            runner.backfill(ref, handle);

            verify(backfillService, times(3)).runBackfillPass(WORKSPACE_ID, handle);
            verify(handle, never()).reportCancelled();
            // Three repositories advanced across the job — the pass that ended the loop is the loop's
            // termination condition, not a failure, so this is a clean success. (In production the second
            // pass is already gated by GitLabHistoricalBackfillService's five-minute COOLDOWN_NORMAL, which
            // is why one click still drains only one batch per repository today.)
            verify(handle, never()).reportWarnings();
        }

        @Test
        void jobThatAdvancedNothingAtAll_reportsWarningsSoItDoesNotLookLikeACleanSuccess() {
            when(backfillService.runBackfillPass(eq(WORKSPACE_ID), eq(handle))).thenReturn(0);

            runner.backfill(ref, handle);

            verify(backfillService, times(1)).runBackfillPass(WORKSPACE_ID, handle);
            verify(handle).reportWarnings();
            verify(handle, never()).reportCancelled();
        }

        /** The loop must re-poll cancellation every iteration, not just before the first pass. */
        @Test
        void cancellationRequestedBetweenPasses_stopsLoopingAndReportsCancelled() {
            when(handle.isCancellationRequested()).thenReturn(false, false, true);
            when(backfillService.runBackfillPass(eq(WORKSPACE_ID), eq(handle))).thenReturn(3);

            runner.backfill(ref, handle);

            // Two productive passes, then the cancel checkpoint ends the loop — without the per-iteration
            // check this would spin on a service that keeps reporting progress.
            verify(backfillService, times(2)).runBackfillPass(WORKSPACE_ID, handle);
            verify(handle).reportCancelled();
            // An abort is not a warning — the job must finalize CANCELLED, not SUCCEEDED_WITH_WARNINGS.
            verify(handle, never()).reportWarnings();
        }

        @Test
        void cancellationRequestedBeforeTheFirstPass_neverTouchesTheVendor() {
            when(handle.isCancellationRequested()).thenReturn(true);

            runner.backfill(ref, handle);

            verify(backfillService, never()).runBackfillPass(anyLong(), any());
            verify(handle).reportCancelled();
            verify(handle, never()).reportWarnings();
        }
    }
}

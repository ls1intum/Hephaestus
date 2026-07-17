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

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
        verify(handle).reportCancelled();
    }

    @Test
    void reconcile_completedWithoutCancellation_doesNotReportCancelled() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null);
        when(handle.isCancellationRequested()).thenReturn(false);

        runner.reconcile(ref, handle, SyncJobType.RECONCILIATION);

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
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

        @Test
        void runsExactlyOnePassEvenWhenRepositoriesAdvanced() {
            when(backfillService.runBackfillPass(eq(WORKSPACE_ID), eq(handle))).thenReturn(2);

            runner.backfill(ref, handle);

            // One batch per repository, then return. A second pass could only skip what this one just
            // advanced — every repository that did work is on a five-minute cooldown (proven by
            // GitLabHistoricalBackfillServiceTest#secondPassIsGatedByTheCooldownOfTheFirst). Passing the
            // handle is what makes progress and cancellation observable to the service.
            verify(backfillService, times(1)).runBackfillPass(WORKSPACE_ID, handle);
            verify(handle, never()).reportCancelled();
            verify(handle, never()).reportWarnings();
        }

        @Test
        void passThatAdvancedNothing_reportsWarningsSoTheJobDoesNotLookLikeACleanSuccess() {
            when(backfillService.runBackfillPass(eq(WORKSPACE_ID), eq(handle))).thenReturn(0);

            runner.backfill(ref, handle);

            verify(handle).reportWarnings();
            verify(handle, never()).reportCancelled();
        }

        @Test
        void cancellationObservedDuringThePass_reportsCancelledAndNotWarnings() {
            when(handle.isCancellationRequested()).thenReturn(true);
            // The pass returns early on its own cancel checkpoint, having advanced nothing.
            when(backfillService.runBackfillPass(anyLong(), any())).thenReturn(0);

            runner.backfill(ref, handle);

            verify(handle).reportCancelled();
            // An abort is not a warning — the job must finalize CANCELLED, not SUCCEEDED_WITH_WARNINGS.
            verify(handle, never()).reportWarnings();
        }
    }
}

package de.tum.cit.aet.hephaestus.integration.scm.github.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.BackfillProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.FilterProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.AuthMode;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.GithubDataSyncScheduler;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.backfill.GitHubHistoricalBackfillService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GithubIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;

    @Mock
    private GithubDataSyncScheduler dataSyncScheduler;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private GitHubHistoricalBackfillService backfillService;

    @Mock
    private SyncJobHandle handle;

    private GithubIntegrationSyncRunner runner;
    private IntegrationRef ref;

    @BeforeEach
    void setUp() {
        SyncSchedulerProperties properties = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            new BackfillProperties(true, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of()),
            null,
            null
        );
        runner = new GithubIntegrationSyncRunner(dataSyncScheduler, syncTargetProvider, backfillService, properties);
        ref = new IntegrationRef(IntegrationKind.GITHUB, WORKSPACE_ID, "100");
    }

    private static SyncTarget pendingTarget(long id, String repoName) {
        return new SyncTarget(
            id,
            WORKSPACE_ID,
            null,
            null,
            AuthMode.INSTALLATION_APP,
            repoName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null, // issueBackfillHighWaterMark (not initialized -> isBackfillComplete() == false)
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Nested
    class Reconcile {

        @Test
        void delegatesToHandleAwareDataSyncServiceEntryPoint() {
            runner.reconcile(ref, handle);

            verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
            verify(handle, never()).reportCancelled();
        }

        @Test
        void reportsCancelledWhenTheHandleIsStillCancelledOnReturn() {
            when(handle.isCancellationRequested()).thenReturn(true);

            runner.reconcile(ref, handle);

            verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
            verify(handle).reportCancelled();
        }
    }

    @Nested
    class Backfill {

        @Test
        void reportsUnsupportedWhenBackfillIsDisabled() {
            SyncSchedulerProperties disabled = new SyncSchedulerProperties(
                true,
                7,
                "0 0 3 * * *",
                15,
                new BackfillProperties(false, 50, 100, 60),
                new FilterProperties(Set.of(), Set.of(), Set.of()),
                null,
                null
            );
            GithubIntegrationSyncRunner disabledRunner = new GithubIntegrationSyncRunner(
                dataSyncScheduler,
                syncTargetProvider,
                backfillService,
                disabled
            );

            assertThat(disabledRunner.supportsBackfill()).isFalse();
        }

        @Test
        void runsUntilNoRepositoriesArePending() {
            SyncTarget targetA = pendingTarget(1L, "acme/repo-a");
            SyncTarget targetB = pendingTarget(2L, "acme/repo-b");
            when(syncTargetProvider.getSyncTargetsForScope(WORKSPACE_ID)).thenReturn(
                List.of(targetA, targetB),
                List.of()
            );
            when(handle.isCancellationRequested()).thenReturn(false);
            when(backfillService.runBackfillBatch(any(), anyInt())).thenReturn(true);

            runner.backfill(ref, handle);

            verify(backfillService).runBackfillBatch(eq(targetA), eq(50));
            verify(backfillService).runBackfillBatch(eq(targetB), eq(50));
            verify(handle, times(2)).progress(any(), isNull(), any());
            verify(handle, never()).reportCancelled();
        }

        @Test
        void stopsProcessingRemainingRepositoriesOnceCancelled() {
            SyncTarget targetA = pendingTarget(1L, "acme/repo-a");
            SyncTarget targetB = pendingTarget(2L, "acme/repo-b");
            SyncTarget targetC = pendingTarget(3L, "acme/repo-c");
            when(syncTargetProvider.getSyncTargetsForScope(WORKSPACE_ID)).thenReturn(
                List.of(targetA, targetB, targetC)
            );
            // outer-check(false) -> inner: targetA(false, processed) -> targetB(true, cancel: break) -> outer-check(true, stop)
            when(handle.isCancellationRequested()).thenReturn(false, false, true, true);
            when(backfillService.runBackfillBatch(any(), anyInt())).thenReturn(true);

            runner.backfill(ref, handle);

            verify(backfillService, times(1)).runBackfillBatch(any(), anyInt());
            verify(backfillService).runBackfillBatch(eq(targetA), anyInt());
            verify(backfillService, never()).runBackfillBatch(eq(targetB), anyInt());
            verify(backfillService, never()).runBackfillBatch(eq(targetC), anyInt());
            verify(handle).reportCancelled();
        }

        @Test
        void cancelledBeforeFirstBatch_neverCallsRunBackfillBatch() {
            when(handle.isCancellationRequested()).thenReturn(true);

            runner.backfill(ref, handle);

            verify(backfillService, never()).runBackfillBatch(any(), anyInt());
            verify(syncTargetProvider, never()).getSyncTargetsForScope(any());
            verify(handle).reportCancelled();
        }

        @Test
        void noProgressAcrossAllPendingRepositories_stopsInsteadOfSpinning() {
            SyncTarget targetA = pendingTarget(1L, "acme/repo-a");
            SyncTarget targetB = pendingTarget(2L, "acme/repo-b");
            when(syncTargetProvider.getSyncTargetsForScope(WORKSPACE_ID)).thenReturn(List.of(targetA, targetB));
            when(handle.isCancellationRequested()).thenReturn(false);
            when(backfillService.runBackfillBatch(any(), anyInt())).thenReturn(false);

            runner.backfill(ref, handle);

            verify(syncTargetProvider, times(1)).getSyncTargetsForScope(WORKSPACE_ID);
            verify(backfillService, times(2)).runBackfillBatch(any(), anyInt());
            verify(handle).reportWarnings();
        }
    }
}

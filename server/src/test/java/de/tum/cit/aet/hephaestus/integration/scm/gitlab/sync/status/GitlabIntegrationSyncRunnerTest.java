package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link GitlabIntegrationSyncRunner}. Focus: {@code reconcile} reuses
 * {@link GitLabWorkspaceDataSyncTrigger}'s cancellable overload — the design's "no refactor
 * needed" reuse — and that the {@link SyncJobHandle}'s cancellation flag is the exact
 * {@link BooleanSupplier} threaded through, so a cancel request set on the handle is observed by
 * the sync body without an intermediate copy going stale.
 */
@Tag("unit")
class GitlabIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private GitLabWorkspaceDataSyncTrigger trigger;

    @Mock
    private SyncJobHandle handle;

    private GitlabIntegrationSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new GitlabIntegrationSyncRunner(trigger);
    }

    @Test
    void shouldRejectBackfillCall() {
        assertThatThrownBy(() ->
            runner.backfill(new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null), handle)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconcile_threadsTheHandleCancellationSupplierAndReportsCancelledWhenAborted() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");
        when(handle.isCancellationRequested()).thenReturn(true);

        // The runner hands the trigger a BooleanSupplier that reads live from the handle (not a stale
        // copy) — exercise it inside the stub to prove the threading, then assert the abort is labeled.
        doAnswer(inv -> {
            BooleanSupplier cancelled = inv.getArgument(1);
            assertThat(cancelled.getAsBoolean()).isTrue();
            return null;
        })
            .when(trigger)
            .syncAllRepositories(eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.any(BooleanSupplier.class));

        runner.reconcile(ref, handle);

        verify(trigger).syncAllRepositories(eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.any(BooleanSupplier.class));
        // Flag still set on return -> the pass aborted -> job must finalize CANCELLED.
        verify(handle).reportCancelled();
    }

    @Test
    void reconcile_completedWithoutCancellation_doesNotReportCancelled() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null);
        when(handle.isCancellationRequested()).thenReturn(false);

        runner.reconcile(ref, handle);

        verify(trigger).syncAllRepositories(eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.any(BooleanSupplier.class));
        verify(handle, org.mockito.Mockito.never()).reportCancelled();
    }
}

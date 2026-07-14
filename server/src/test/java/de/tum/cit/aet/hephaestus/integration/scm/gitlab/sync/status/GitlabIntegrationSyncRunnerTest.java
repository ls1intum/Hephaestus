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
    void shouldReportGitlabKind() {
        assertThat(runner.kind()).isEqualTo(IntegrationKind.GITLAB);
    }

    @Test
    void shouldNotSupportBackfill() {
        assertThat(runner.supportsBackfill()).isFalse();
    }

    @Test
    void shouldRejectBackfillCall() {
        assertThatThrownBy(() ->
            runner.backfill(new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null), handle)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDelegateReconcileToTriggerWithWorkspaceId() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");

        runner.reconcile(ref, handle);

        verify(trigger).syncAllRepositories(eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.any(BooleanSupplier.class));
    }

    @Test
    void shouldThreadHandleCancellationThroughToTrigger() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null);
        when(handle.isCancellationRequested()).thenReturn(false, true);

        // Capture the BooleanSupplier the runner hands to the trigger and exercise it directly —
        // proving cancellation observed at the runner's call site is not a stale copy but reads
        // live from the handle (the cheap per-repository check the sync loop polls).
        doAnswer(inv -> {
            BooleanSupplier cancelled = inv.getArgument(1);
            assertThat(cancelled.getAsBoolean()).isFalse();
            assertThat(cancelled.getAsBoolean()).isTrue();
            return null;
        })
            .when(trigger)
            .syncAllRepositories(eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.any(BooleanSupplier.class));

        runner.reconcile(ref, handle);

        verify(handle, org.mockito.Mockito.times(2)).isCancellationRequested();
    }
}

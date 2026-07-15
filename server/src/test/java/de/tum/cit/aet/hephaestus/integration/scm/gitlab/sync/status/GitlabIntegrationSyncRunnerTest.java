package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.GitlabDataSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/** Unit tests for {@link GitlabIntegrationSyncRunner}. */
@Tag("unit")
class GitlabIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private GitlabDataSyncScheduler dataSyncScheduler;

    @Mock
    private SyncJobHandle handle;

    private GitlabIntegrationSyncRunner runner;

    @BeforeEach
    void setUp() {
        runner = new GitlabIntegrationSyncRunner(dataSyncScheduler);
    }

    @Test
    void shouldRejectBackfillCall() {
        assertThatThrownBy(() ->
            runner.backfill(new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null), handle)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconcile_reportsCancelledWhenAborted() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");
        when(handle.isCancellationRequested()).thenReturn(true);

        runner.reconcile(ref, handle);

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
        verify(handle).reportCancelled();
    }

    @Test
    void reconcile_completedWithoutCancellation_doesNotReportCancelled() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, null);
        when(handle.isCancellationRequested()).thenReturn(false);

        runner.reconcile(ref, handle);

        verify(dataSyncScheduler).syncWorkspaceNow(WORKSPACE_ID, handle);
        verify(handle, never()).reportCancelled();
    }
}

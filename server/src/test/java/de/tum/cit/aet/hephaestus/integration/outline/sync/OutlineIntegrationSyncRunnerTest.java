package de.tum.cit.aet.hephaestus.integration.outline.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OutlineIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE = 7L;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    @Mock
    private SyncJobHandle handle;

    private OutlineIntegrationSyncRunner runner;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        runner = new OutlineIntegrationSyncRunner(syncScheduler);
    }

    @Test
    void reconcile_delegatesToTheWorkspacePass_threadingAProgressListener() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.OUTLINE, WORKSPACE, "team-1");

        runner.reconcile(ref, handle);

        verify(syncScheduler).syncWorkspaceNow(eq(WORKSPACE), any(OutlineSyncProgressListener.class));
        verify(handle, never()).reportCancelled();
    }

    @Test
    void reconcile_reportsCancelledWhenTheHandleIsStillCancelledOnReturn() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.OUTLINE, WORKSPACE, "team-1");
        when(handle.isCancellationRequested()).thenReturn(true);

        runner.reconcile(ref, handle);

        verify(syncScheduler).syncWorkspaceNow(eq(WORKSPACE), any(OutlineSyncProgressListener.class));
        verify(handle).reportCancelled();
    }

    @Test
    void reconcile_propagatesPartialFailuresAsWarnings() {
        doAnswer(invocation -> {
            OutlineSyncProgressListener listener = invocation.getArgument(1);
            listener.onWarning();
            return null;
        })
            .when(syncScheduler)
            .syncWorkspaceNow(eq(WORKSPACE), any(OutlineSyncProgressListener.class));

        runner.reconcile(new IntegrationRef(IntegrationKind.OUTLINE, WORKSPACE, "team-1"), handle);

        verify(handle).reportWarnings();
    }
}

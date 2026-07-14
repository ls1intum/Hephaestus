package de.tum.cit.aet.hephaestus.integration.outline.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * {@link OutlineIntegrationSyncRunner} is the manual-trigger body {@code SyncStatusService} dispatches by
 * {@link IntegrationKind} — it must delegate to the exact same workspace pass the old (now-absorbed)
 * {@code POST /connections/outline/sync} ran, and must NOT itself create another sync job (the job that
 * invoked it already exists and is RUNNING).
 */
class OutlineIntegrationSyncRunnerTest extends BaseUnitTest {

    private static final long WORKSPACE = 7L;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    private OutlineIntegrationSyncRunner runner;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        runner = new OutlineIntegrationSyncRunner(syncScheduler);
    }

    @Test
    void kind_isOutline() {
        assertThat(runner.kind()).isEqualTo(IntegrationKind.OUTLINE);
    }

    @Test
    void supportsBackfill_isFalse() {
        // Outline has no separate backfill phase — every pass is the same full reconcile.
        assertThat(runner.supportsBackfill()).isFalse();
    }

    @Test
    void reconcile_delegatesToTheWorkspacePass_threadingAProgressListener() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.OUTLINE, WORKSPACE, "team-1");

        runner.reconcile(ref, null);

        verify(syncScheduler).syncWorkspaceNow(eq(WORKSPACE), any(OutlineSyncProgressListener.class));
    }
}

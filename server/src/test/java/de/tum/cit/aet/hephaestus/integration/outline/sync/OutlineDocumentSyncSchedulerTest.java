package de.tum.cit.aet.hephaestus.integration.outline.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Job-recording wiring for {@link OutlineDocumentSyncScheduler} — the replacement for Outline's bespoke
 * {@code syncsInFlight} guard. The scheduler's fan-outs ({@code syncAllNow}, {@code catchUp}) now record
 * a {@code RECONCILIATION} {@link de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob} per workspace
 * through {@link SyncJobService}; a connection that already has an active job is skipped (the job guard
 * is the dedup), and the catch-up tick only ever records a job for a workspace the fan-out query already
 * says has pending work.
 */
class OutlineDocumentSyncSchedulerTest extends BaseUnitTest {

    private static final long WORKSPACE_1 = 7L;
    private static final long WORKSPACE_2 = 8L;
    private static final long CONNECTION_1 = 71L;
    private static final long CONNECTION_2 = 81L;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineDocumentSyncService syncService;

    @Mock
    private OutlineCollectionRepository collectionRepository;

    @Mock
    private SyncJobService syncJobService;

    @Mock
    private Connection connection1;

    @Mock
    private Connection connection2;

    private OutlineDocumentSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutlineDocumentSyncScheduler(
            connectionService,
            syncService,
            collectionRepository,
            syncJobService
        );
        lenientStub();
    }

    private void lenientStub() {
        org.mockito.Mockito.lenient().when(connection1.getId()).thenReturn(CONNECTION_1);
        org.mockito.Mockito.lenient().when(connection2.getId()).thenReturn(CONNECTION_2);
    }

    /** Stubs {@code syncJobService.run(...)} to actually invoke the body, as the real template would. */
    private void runJobsSynchronously() {
        doAnswer(invocation -> {
            Consumer<SyncJobHandle> body = invocation.getArgument(1);
            body.accept(null);
            return null;
        })
            .when(syncJobService)
            .run(any(SyncJobRequest.class), any());
    }

    @Test
    void syncAllNow_recordsAReconciliationScheduledJob_perActiveWorkspace_andRunsTheWorkspacePass() {
        when(connectionService.findWorkspaceIdsWithActiveConnection(IntegrationKind.OUTLINE)).thenReturn(
            List.of(WORKSPACE_1, WORKSPACE_2)
        );
        when(connectionService.findActive(WORKSPACE_1, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection1));
        when(connectionService.findActive(WORKSPACE_2, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection2));
        runJobsSynchronously();

        int attempted = scheduler.syncAllNow();

        assertThat(attempted).isEqualTo(2);
        ArgumentCaptor<SyncJobRequest> captor = ArgumentCaptor.forClass(SyncJobRequest.class);
        verify(syncJobService, times(2)).run(captor.capture(), any());
        assertThat(captor.getAllValues())
            .extracting(
                SyncJobRequest::workspaceId,
                SyncJobRequest::connectionId,
                SyncJobRequest::type,
                SyncJobRequest::trigger
            )
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(
                    WORKSPACE_1,
                    CONNECTION_1,
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.SCHEDULED
                ),
                org.assertj.core.groups.Tuple.tuple(
                    WORKSPACE_2,
                    CONNECTION_2,
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.SCHEDULED
                )
            );
        verify(syncService).syncWorkspace(eq(WORKSPACE_1), any(OutlineSyncProgressListener.class));
        verify(syncService).syncWorkspace(eq(WORKSPACE_2), any(OutlineSyncProgressListener.class));
    }

    @Test
    void syncAllNow_oneWorkspaceHasAnActiveJobAlready_isSkipped_theOtherStillRuns() {
        when(connectionService.findWorkspaceIdsWithActiveConnection(IntegrationKind.OUTLINE)).thenReturn(
            List.of(WORKSPACE_1, WORKSPACE_2)
        );
        when(connectionService.findActive(WORKSPACE_1, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection1));
        when(connectionService.findActive(WORKSPACE_2, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection2));

        SyncJob activeJob = mock(SyncJob.class);
        Connection activeConnection = mock(Connection.class);
        when(activeConnection.getId()).thenReturn(CONNECTION_1);
        when(activeJob.getConnection()).thenReturn(activeConnection);
        when(activeJob.getId()).thenReturn(999L);

        doAnswer(invocation -> {
            SyncJobRequest request = invocation.getArgument(0);
            if (request.connectionId() == CONNECTION_1) {
                throw new SyncJobConflictException(activeJob);
            }
            Consumer<SyncJobHandle> body = invocation.getArgument(1);
            body.accept(null);
            return null;
        })
            .when(syncJobService)
            .run(any(SyncJobRequest.class), any());

        assertThatCode(() -> scheduler.syncAllNow()).doesNotThrowAnyException();

        verify(syncService, never()).syncWorkspace(eq(WORKSPACE_1), any(OutlineSyncProgressListener.class));
        verify(syncService).syncWorkspace(eq(WORKSPACE_2), any(OutlineSyncProgressListener.class));
    }

    @Test
    void syncAllNow_workspaceLostItsActiveConnectionMeanwhile_isSilentlySkipped() {
        when(connectionService.findWorkspaceIdsWithActiveConnection(IntegrationKind.OUTLINE)).thenReturn(
            List.of(WORKSPACE_1)
        );
        when(connectionService.findActive(WORKSPACE_1, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        int attempted = scheduler.syncAllNow();

        assertThat(attempted).isEqualTo(1);
        verify(syncJobService, never()).run(any(), any());
    }

    @Test
    void catchUp_recordsAReconciliationSystemJob_onlyForWorkspacesTheFanOutSaysHavePendingWork() {
        when(collectionRepository.findDistinctWorkspaceIdsWithPendingSync()).thenReturn(List.of(WORKSPACE_1));
        when(connectionService.findActive(WORKSPACE_1, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection1));
        runJobsSynchronously();

        scheduler.catchUp();

        ArgumentCaptor<SyncJobRequest> captor = ArgumentCaptor.forClass(SyncJobRequest.class);
        verify(syncJobService, times(1)).run(captor.capture(), any());
        assertThat(captor.getValue().workspaceId()).isEqualTo(WORKSPACE_1);
        assertThat(captor.getValue().connectionId()).isEqualTo(CONNECTION_1);
        assertThat(captor.getValue().type()).isEqualTo(SyncJobType.RECONCILIATION);
        assertThat(captor.getValue().trigger()).isEqualTo(SyncJobTrigger.SYSTEM);
        verify(syncService).syncPendingCollections(eq(WORKSPACE_1), any(OutlineSyncProgressListener.class));
    }

    @Test
    void catchUp_noWorkspaceHasPendingWork_recordsNoJobAndRunsNothing() {
        when(collectionRepository.findDistinctWorkspaceIdsWithPendingSync()).thenReturn(List.of());

        scheduler.catchUp();

        verify(syncJobService, never()).run(any(), any());
        verify(syncService, never()).syncPendingCollections(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void catchUp_jobAlreadyActive_isSkippedWithoutPropagating() {
        when(collectionRepository.findDistinctWorkspaceIdsWithPendingSync()).thenReturn(List.of(WORKSPACE_1));
        when(connectionService.findActive(WORKSPACE_1, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection1));
        SyncJob activeJob = mock(SyncJob.class);
        Connection activeConnection = mock(Connection.class);
        when(activeConnection.getId()).thenReturn(CONNECTION_1);
        when(activeJob.getConnection()).thenReturn(activeConnection);
        when(activeJob.getId()).thenReturn(1000L);
        doThrow(new SyncJobConflictException(activeJob)).when(syncJobService).run(any(), any());

        assertThatCode(() -> scheduler.catchUp()).doesNotThrowAnyException();
        verify(syncService, never()).syncPendingCollections(eq(WORKSPACE_1), any());
    }
}

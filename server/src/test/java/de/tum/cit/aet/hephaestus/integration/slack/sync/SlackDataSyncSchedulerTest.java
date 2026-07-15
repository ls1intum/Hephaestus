package de.tum.cit.aet.hephaestus.integration.slack.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** The scheduler's fan-out, its disconnected-workspace short circuit, and its job-recording wrapper. */
@Tag("unit")
class SlackDataSyncSchedulerTest extends BaseUnitTest {

    private static final long WS = 42L;
    private static final long CONNECTION_ID = 7L;

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Mock
    private SlackChannelMetadataRefresher metadataRefresher;

    @Mock
    private SlackChannelHistorySyncService historySyncService;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private SyncJobService syncJobService;

    private SlackDataSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SlackDataSyncScheduler(
            monitoredChannelRepository,
            metadataRefresher,
            historySyncService,
            connectionService,
            syncJobService,
            new SlackSyncProperties("0 0 4 * * *", 10, 15, Duration.ZERO, true, 5, true)
        );
    }

    @Test
    void workspaceWithoutAnActiveSlackConnection_spendsNoBudgetAndTouchesNothing() {
        // Every Slack call would fail on token resolution anyway; attempting them would burn a rate-limit slot
        // (and its pacing sleep) and log one warning per channel for a workspace that simply is not connected.
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.empty());

        var summary = scheduler.syncWorkspaceNow(WS);

        assertThat(summary.requestsUsed()).isZero();
        assertThat(summary.channels()).isZero();
        verify(metadataRefresher, never()).refreshWorkspace(anyLong());
        verify(historySyncService, never()).syncWorkspace(anyLong(), any());
    }

    @Test
    void connectedWorkspace_refreshesMetadataThenReconcilesHistory() {
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.of(mock(Connection.class)));
        when(historySyncService.syncWorkspace(eq(WS), any(BooleanSupplier.class))).thenReturn(
            new SlackChannelHistorySyncService.WorkspaceSyncSummary(2, 2, 0, 5L, 3, false, 0)
        );

        var summary = scheduler.syncWorkspaceNow(WS);

        verify(metadataRefresher).refreshWorkspace(WS);
        verify(historySyncService).syncWorkspace(eq(WS), any(BooleanSupplier.class));
        assertThat(summary.ingested()).isEqualTo(5L);
    }

    @Test
    void cron_isolatesAFailingWorkspaceFromTheRest() {
        when(monitoredChannelRepository.findDistinctWorkspaceIdsByConsentState("ACTIVE")).thenReturn(List.of(1L, 2L));
        when(connectionService.findActive(1L, IntegrationKind.SLACK)).thenThrow(new IllegalStateException("boom"));
        when(connectionService.findActive(2L, IntegrationKind.SLACK)).thenReturn(Optional.empty());

        scheduler.syncDataCron();

        // Workspace 1 blew up; workspace 2 was still evaluated.
        verify(connectionService).findActive(2L, IntegrationKind.SLACK);
    }

    @Test
    void cron_workspaceWithNoActiveConnection_recordsNoJobAndTouchesNothing() {
        when(monitoredChannelRepository.findDistinctWorkspaceIdsByConsentState("ACTIVE")).thenReturn(List.of(WS));
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.empty());

        scheduler.syncDataCron();

        verify(syncJobService, never()).run(any(), any());
        verify(historySyncService, never()).syncWorkspace(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cron_connectedWorkspace_recordsAReconciliationJobWhoseBodyRunsTheHistorySync() {
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn(CONNECTION_ID);
        when(monitoredChannelRepository.findDistinctWorkspaceIdsByConsentState("ACTIVE")).thenReturn(List.of(WS));
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.of(connection));
        when(historySyncService.syncWorkspace(eq(WS), any(BooleanSupplier.class))).thenReturn(
            new SlackChannelHistorySyncService.WorkspaceSyncSummary(2, 2, 0, 4L, 2, false, 0)
        );

        scheduler.syncDataCron();

        ArgumentCaptor<SyncJobRequest> requestCaptor = ArgumentCaptor.forClass(SyncJobRequest.class);
        ArgumentCaptor<Consumer<SyncJobHandle>> bodyCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(syncJobService).run(requestCaptor.capture(), bodyCaptor.capture());

        SyncJobRequest request = requestCaptor.getValue();
        assertThat(request.workspaceId()).isEqualTo(WS);
        assertThat(request.connectionId()).isEqualTo(CONNECTION_ID);
        assertThat(request.kind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(request.type()).isEqualTo(SyncJobType.RECONCILIATION);
        assertThat(request.trigger()).isEqualTo(SyncJobTrigger.SCHEDULED);

        // The job template only ever creates the row; the body itself isn't invoked until SyncJobService
        // dispatches it. Drive it here with a stub handle to prove it delegates to the same history sync path.
        SyncJobHandle handle = mock(SyncJobHandle.class);
        bodyCaptor.getValue().accept(handle);

        verify(historySyncService).syncWorkspace(eq(WS), any(BooleanSupplier.class));
        verify(handle).progress(eq(2), eq(2), any());
    }

    @Test
    void cron_skipsWorkspaceWhoseConnectionAlreadyHasAnActiveSyncJob() {
        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn(CONNECTION_ID);
        when(monitoredChannelRepository.findDistinctWorkspaceIdsByConsentState("ACTIVE")).thenReturn(List.of(WS));
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.of(connection));

        SyncJob activeJob = mock(SyncJob.class);
        when(activeJob.getConnection()).thenReturn(connection);
        doThrow(new SyncJobConflictException(activeJob)).when(syncJobService).run(any(), any());

        assertThatCode(() -> scheduler.syncDataCron()).doesNotThrowAnyException();

        verify(syncJobService).run(any(), any());
    }
}

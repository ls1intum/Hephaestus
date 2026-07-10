package de.tum.cit.aet.hephaestus.integration.slack.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/** The scheduler's fan-out and its disconnected-workspace short circuit. */
@Tag("unit")
class SlackDataSyncSchedulerTest extends BaseUnitTest {

    private static final long WS = 42L;

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Mock
    private SlackChannelMetadataRefresher metadataRefresher;

    @Mock
    private SlackChannelHistorySyncService historySyncService;

    @Mock
    private ConnectionService connectionService;

    private SlackDataSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SlackDataSyncScheduler(
            monitoredChannelRepository,
            metadataRefresher,
            historySyncService,
            connectionService,
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
        verify(historySyncService, never()).syncWorkspace(anyLong());
    }

    @Test
    void connectedWorkspace_refreshesMetadataThenReconcilesHistory() {
        when(connectionService.findActive(WS, IntegrationKind.SLACK)).thenReturn(Optional.of(mock(Connection.class)));
        when(historySyncService.syncWorkspace(WS)).thenReturn(
            new SlackChannelHistorySyncService.WorkspaceSyncSummary(2, 2, 0, 5L, 3, false)
        );

        var summary = scheduler.syncWorkspaceNow(WS);

        verify(metadataRefresher).refreshWorkspace(WS);
        verify(historySyncService).syncWorkspace(WS);
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
}

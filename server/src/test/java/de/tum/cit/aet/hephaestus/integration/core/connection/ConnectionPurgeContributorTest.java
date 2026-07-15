package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ConnectionPurgeContributorTest extends BaseUnitTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private Connection connection;

    @Test
    void activeSyncConflictAbortsWorkspacePurgeContribution() {
        when(connection.getState()).thenReturn(IntegrationState.ACTIVE);
        when(connectionRepository.findByWorkspaceId(42L)).thenReturn(List.of(connection));
        doThrow(new ConnectionBusyException(7L, 99L))
            .when(connectionService)
            .transition(eq(connection), any(ConnectionService.TransitionRequest.class));

        ConnectionPurgeContributor contributor = new ConnectionPurgeContributor(
            connectionRepository,
            connectionService
        );

        assertThatThrownBy(() -> contributor.deleteWorkspaceData(42L))
            .isInstanceOf(ConnectionBusyException.class)
            .hasMessageContaining("active sync job 99");
    }
}

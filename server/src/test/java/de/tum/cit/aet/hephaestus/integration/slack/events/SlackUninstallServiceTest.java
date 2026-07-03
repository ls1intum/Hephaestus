package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspacePurgeAdapter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Uninstall-routing unit tests: an {@code app_uninstalled}/{@code tokens_revoked} event flips the Slack
 * Connection to UNINSTALLED and then purges the workspace's Slack content; an unknown team is a safe no-op.
 */
class SlackUninstallServiceTest extends BaseUnitTest {

    private static final long WORKSPACE = 42L;
    private static final String TEAM = "T1";

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private SlackWorkspacePurgeAdapter purgeAdapter;

    @Mock
    private Connection connection;

    private SlackUninstallService service() {
        return new SlackUninstallService(workspaceResolver, connectionService, purgeAdapter);
    }

    @Test
    void appUninstalled_flipsConnectionUninstalled_thenPurges() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        when(connectionService.findActive(WORKSPACE, IntegrationKind.SLACK)).thenReturn(Optional.of(connection));

        service().onUninstall(TEAM, "app_uninstalled");

        ArgumentCaptor<ConnectionService.TransitionRequest> captor = ArgumentCaptor.forClass(
            ConnectionService.TransitionRequest.class
        );
        verify(connectionService).transition(eq(connection), captor.capture());
        assertThat(captor.getValue().next()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(captor.getValue().eventType()).isEqualTo("APP_UNINSTALLED");
        // Content is purged after the connection flip (adapter reused).
        verify(purgeAdapter).deleteWorkspaceData(WORKSPACE);
    }

    @Test
    void tokensRevoked_recordsRevokedEventType() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE));
        when(connectionService.findActive(WORKSPACE, IntegrationKind.SLACK)).thenReturn(Optional.of(connection));

        service().onUninstall(TEAM, "tokens_revoked");

        ArgumentCaptor<ConnectionService.TransitionRequest> captor = ArgumentCaptor.forClass(
            ConnectionService.TransitionRequest.class
        );
        verify(connectionService).transition(eq(connection), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("TOKENS_REVOKED");
    }

    @Test
    void unknownTeam_isNoOp() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.empty());

        service().onUninstall(TEAM, "app_uninstalled");

        verify(connectionService, never()).transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(purgeAdapter);
    }
}

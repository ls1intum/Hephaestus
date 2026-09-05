package de.tum.cit.aet.hephaestus.workspace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WorkspaceSettingsServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ConfigAuditPort configAudit;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /** Re-entering a token is the way out for one the server cannot read, so it must not read it. */
    @Test
    void shouldRotateATokenWithoutReadingTheOldOne() {
        WorkspaceSettingsService service = new WorkspaceSettingsService(
                workspaceRepository,
                configAudit,
                connectionService,
                eventPublisher,
                Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC));
        Workspace workspace = mock(Workspace.class);
        Connection connection = mock(Connection.class);
        when(workspaceRepository.findById(7L)).thenReturn(Optional.of(workspace));
        when(connectionService.findActiveProviderKind(7L)).thenReturn(Optional.of(IntegrationKind.GITLAB));
        when(connectionService.findActive(7L, IntegrationKind.GITLAB)).thenReturn(Optional.of(connection));
        when(connection.hasCredentials()).thenReturn(true);

        service.updateToken(7L, "glpat-new");

        verify(connectionService).rotateBearerToken(eq(7L), eq(IntegrationKind.GITLAB), any(BearerToken.class));
        verify(connectionService, never()).findActiveBearerToken(any(Long.class), any());
    }
}

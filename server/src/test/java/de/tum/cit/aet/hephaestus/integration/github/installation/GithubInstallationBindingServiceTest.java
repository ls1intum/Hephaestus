package de.tum.cit.aet.hephaestus.integration.github.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-level guard rails for the binding service: happy path, cross-workspace collision,
 * 404 paths. No Spring context — pure Mockito so the suite stays fast and isolated from
 * upstream scaffolding refactors.
 */
@DisplayName("GithubInstallationBindingService — unit")
class GithubInstallationBindingServiceTest extends BaseUnitTest {

    @Mock
    private GithubInstallationUnboundRepository unboundRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private Workspace workspace;

    private GithubInstallationBindingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GithubInstallationBindingService(
            unboundRepository, connectionRepository, connectionService, workspaceRepository
        );
    }

    @Test
    @DisplayName("happy path: creates Connection, transitions to ACTIVE, deletes unbound row")
    void happyPath() {
        long installationId = 100_001L;
        long workspaceId = 42L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);

        GithubInstallationUnbound unbound = new GithubInstallationUnbound(installationId);
        unbound.setAccountLogin("acme-corp");
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(List.of());
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId, IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(Optional.empty());
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> {
                Connection c = inv.getArgument(0);
                c.setState(IntegrationState.ACTIVE);
                return c;
            });

        Connection bound = service.bind(installationId, workspaceId, "admin@example.com");

        assertThat(bound.getState()).isEqualTo(IntegrationState.ACTIVE);

        ArgumentCaptor<Connection> savedConnection = ArgumentCaptor.forClass(Connection.class);
        verify(connectionRepository).save(savedConnection.capture());
        Connection created = savedConnection.getValue();
        assertThat(created.getKind()).isEqualTo(IntegrationKind.GITHUB);
        assertThat(created.getInstanceKey()).isEqualTo(Long.toString(installationId));
        ConnectionConfig.GitHubAppConfig cfg = (ConnectionConfig.GitHubAppConfig) created.getConfig();
        assertThat(cfg.installationId()).isEqualTo(installationId);
        assertThat(cfg.orgLogin()).isEqualTo("acme-corp");

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().next()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(req.getValue().eventType()).isEqualTo("BIND");
        assertThat(req.getValue().correlationId()).isEqualTo("bind-" + installationId);

        verify(unboundRepository).delete(unbound);
    }

    @Test
    @DisplayName("cross-workspace collision: refuses to bind to a different workspace")
    void crossWorkspaceCollision() {
        long installationId = 100_002L;
        long workspaceA = 7L;
        long workspaceB = 99L;

        Workspace ownerWorkspace = org.mockito.Mockito.mock(Workspace.class);
        when(ownerWorkspace.getId()).thenReturn(workspaceA);

        GithubInstallationUnbound unbound = new GithubInstallationUnbound(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));

        Workspace targetWorkspace = org.mockito.Mockito.mock(Workspace.class);
        org.mockito.Mockito.lenient().when(targetWorkspace.getId()).thenReturn(workspaceB);
        when(workspaceRepository.findById(workspaceB)).thenReturn(Optional.of(targetWorkspace));

        Connection preExisting = new Connection(
            ownerWorkspace,
            IntegrationKind.GITHUB,
            Long.toString(installationId),
            new ConnectionConfig.GitHubAppConfig(installationId, "team-a", null, java.util.Set.of())
        );
        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(List.of(preExisting));

        assertThatThrownBy(() -> service.bind(installationId, workspaceB, "admin@example.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already bound to workspace=" + workspaceA);

        verify(connectionRepository, never()).save(any(Connection.class));
        verify(unboundRepository, never()).delete(any(GithubInstallationUnbound.class));
    }

    @Test
    @DisplayName("idempotent re-bind: same workspace, existing connection → transitions without re-creating")
    void idempotentReBind() {
        long installationId = 100_003L;
        long workspaceId = 17L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);

        GithubInstallationUnbound unbound = new GithubInstallationUnbound(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        Connection existing = new Connection(
            workspace, IntegrationKind.GITHUB, Long.toString(installationId),
            new ConnectionConfig.GitHubAppConfig(installationId, null, null, java.util.Set.of())
        );
        existing.setState(IntegrationState.PENDING);

        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(List.of(existing));
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId, IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(Optional.of(existing));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> {
                Connection c = inv.getArgument(0);
                c.setState(IntegrationState.ACTIVE);
                return c;
            });

        Connection result = service.bind(installationId, workspaceId, "admin@example.com");

        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        verify(connectionRepository, never()).save(any(Connection.class));
        verify(connectionService, times(1)).transition(any(Connection.class), any(TransitionRequest.class));
        verify(unboundRepository).delete(unbound);
    }

    @Test
    @DisplayName("already-active connection: no transition call, still deletes unbound row")
    void alreadyActiveSkipsTransition() {
        long installationId = 100_004L;
        long workspaceId = 23L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);

        GithubInstallationUnbound unbound = new GithubInstallationUnbound(installationId);
        when(unboundRepository.findById(installationId)).thenReturn(Optional.of(unbound));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        Connection existing = new Connection(
            workspace, IntegrationKind.GITHUB, Long.toString(installationId),
            new ConnectionConfig.GitHubAppConfig(installationId, null, null, java.util.Set.of())
        );
        existing.setState(IntegrationState.ACTIVE);

        when(connectionRepository.findByKindAndInstanceKey(
            IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(List.of(existing));
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            workspaceId, IntegrationKind.GITHUB, Long.toString(installationId)))
            .thenReturn(Optional.of(existing));

        Connection result = service.bind(installationId, workspaceId, "admin@example.com");

        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        verify(connectionService, never()).transition(any(Connection.class), any(TransitionRequest.class));
        verify(unboundRepository).delete(unbound);
    }

    @Test
    @DisplayName("404 path: missing unbound row → NoSuchElementException")
    void missingUnboundRow() {
        long missing = 999_999L;
        when(unboundRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(missing, 1L, "admin@example.com"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(String.valueOf(missing));

        verify(workspaceRepository, never()).findById(anyLong());
        verify(connectionRepository, never()).save(any(Connection.class));
    }

    @Test
    @DisplayName("404 path: missing workspace → EntityNotFoundException")
    void missingWorkspace() {
        long installationId = 100_005L;
        long workspaceId = 12345L;
        when(unboundRepository.findById(installationId))
            .thenReturn(Optional.of(new GithubInstallationUnbound(installationId)));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(installationId, workspaceId, "admin@example.com"))
            .isInstanceOf(EntityNotFoundException.class);

        verify(connectionRepository, never()).save(any(Connection.class));
        verify(unboundRepository, never()).delete(any(GithubInstallationUnbound.class));
    }

    @Test
    @DisplayName("actorRef threaded through to transition request")
    void actorRefThreadedThrough() {
        long installationId = 100_006L;
        long workspaceId = 8L;
        org.mockito.Mockito.lenient().when(workspace.getId()).thenReturn(workspaceId);
        when(unboundRepository.findById(installationId))
            .thenReturn(Optional.of(new GithubInstallationUnbound(installationId)));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(connectionRepository.findByKindAndInstanceKey(
            eq(IntegrationKind.GITHUB), any(String.class))).thenReturn(List.of());
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(
            anyLong(), eq(IntegrationKind.GITHUB), any(String.class))).thenReturn(Optional.empty());
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class)))
            .thenAnswer(inv -> {
                Connection c = inv.getArgument(0);
                c.setState(IntegrationState.ACTIVE);
                return c;
            });

        service.bind(installationId, workspaceId, "alice@example.com");

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().actorRef()).isEqualTo("alice@example.com");
        assertThat(req.getValue().actorKind()).isEqualTo("ADMIN");
    }
}

package de.tum.cit.aet.hephaestus.integration.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy.ConnectFinalization;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@DisplayName("OAuthCallbackService — unit")
class OAuthCallbackServiceTest extends BaseUnitTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.connection.CredentialBundleConverter credentialBundleConverter;

    private OAuthCallbackService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OAuthCallbackService(
            connectionRepository,
            connectionService,
            workspaceRepository,
            credentialBundleConverter
        );
    }

    @Test
    @DisplayName("findOrCreatePendingConnection reuses PENDING row if present")
    void findOrCreate_reusesPending() {
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(
            connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                42L,
                IntegrationKind.SLACK,
                IntegrationState.PENDING
            )
        ).thenReturn(Optional.of(pending));

        Connection result = service.findOrCreatePendingConnection(42L, IntegrationKind.SLACK);

        assertThat(result).isSameAs(pending);
        verify(workspaceRepository, never()).findById(any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreatePendingConnection reuses ACTIVE row (credential refresh on reconnect)")
    void findOrCreate_reusesActiveForReconnect() {
        Connection active = newConnection(7L, 42L, IntegrationKind.SLACK, "T1", IntegrationState.ACTIVE);
        when(
            connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                42L,
                IntegrationKind.SLACK,
                IntegrationState.PENDING
            )
        ).thenReturn(Optional.empty());
        when(
            connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                42L,
                IntegrationKind.SLACK,
                IntegrationState.ACTIVE
            )
        ).thenReturn(Optional.of(active));

        Connection result = service.findOrCreatePendingConnection(42L, IntegrationKind.SLACK);

        assertThat(result).isSameAs(active);
        verify(workspaceRepository, never()).findById(any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreatePendingConnection creates a fresh row when no PENDING/ACTIVE exists")
    void findOrCreate_createsFreshWhenNoneExists() {
        when(
            connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                any(Long.class),
                any(),
                any()
            )
        ).thenReturn(Optional.empty());
        Workspace workspace = Mockito.mock(Workspace.class);
        when(workspaceRepository.findById(42L)).thenReturn(Optional.of(workspace));
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));

        Connection result = service.findOrCreatePendingConnection(42L, IntegrationKind.SLACK);

        assertThat(result.getKind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(result.getInstanceKey()).isNull();
        assertThat(result.getConfig()).isInstanceOf(ConnectionConfig.SlackConfig.class);
        verify(connectionRepository).save(any(Connection.class));
    }

    @Test
    @DisplayName("findOrCreatePendingConnection throws if workspace doesn't exist")
    void findOrCreate_missingWorkspace_throws() {
        when(
            connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                any(Long.class),
                any(),
                any()
            )
        ).thenReturn(Optional.empty());
        when(workspaceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreatePendingConnection(42L, IntegrationKind.SLACK))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Workspace not found");
    }

    @Test
    @DisplayName(
        "completeConnection stamps instance_key on a fresh PENDING row + transitions ACTIVE with the captured actorRef"
    )
    void complete_stampsInstanceKeyAndTransitions() {
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        ConnectFinalization.Completed completed = new ConnectFinalization.Completed(
            "T123",
            new BearerToken("xoxb", null),
            "Acme"
        );
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class))).thenAnswer(inv -> {
            Connection c = inv.getArgument(0);
            c.setState(IntegrationState.ACTIVE);
            return c;
        });
        when(credentialBundleConverter.encrypt(any(), any())).thenReturn(new byte[] { 0x02, 1, 2, 3 });

        Connection result = service.completeConnection(pending, completed, "alice@example.com");

        assertThat(result.getInstanceKey()).isEqualTo("T123");
        assertThat(result.getDisplayName()).isEqualTo("Acme");
        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(result.getCredentialsAlg()).isEqualTo("aesgcm-v1");

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().next()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(req.getValue().eventType()).isEqualTo("OAUTH_COMPLETE");
        assertThat(req.getValue().actorKind()).isEqualTo("USER");
        assertThat(req.getValue().actorRef()).isEqualTo("alice@example.com");
        assertThat(req.getValue().correlationId()).startsWith("oauth-T123-");
        assertThat(req.getValue().detail()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("completeConnection falls back to 'oauth-callback' actor when actorRef is null")
    void complete_nullActorRef_usesSentinel() {
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        ConnectFinalization.Completed completed = new ConnectFinalization.Completed(
            "T1",
            new BearerToken("t", null),
            null
        );
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        service.completeConnection(pending, completed, null);

        ArgumentCaptor<TransitionRequest> req = ArgumentCaptor.forClass(TransitionRequest.class);
        verify(connectionService).transition(any(Connection.class), req.capture());
        assertThat(req.getValue().actorRef()).isEqualTo(OAuthCallbackService.ACTOR_FALLBACK);
    }

    @Test
    @DisplayName("completeConnection rejects a vendor-returned instance_key that conflicts with the existing one")
    void complete_conflictingInstanceKey_throws() {
        Connection existing = newConnection(7L, 42L, IntegrationKind.SLACK, "T_ORIG", IntegrationState.ACTIVE);
        ConnectFinalization.Completed completed = new ConnectFinalization.Completed(
            "T_NEW",
            new BearerToken("t", null),
            "Renamed"
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.completeConnection(existing, completed, "alice")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("instance_key");
    }

    @Test
    @DisplayName("completeConnection propagates transition guard rejection (IllegalStateException)")
    void complete_transitionGuardRejects_throwsThrough() {
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        ConnectFinalization.Completed completed = new ConnectFinalization.Completed(
            "T1",
            new BearerToken("t", null),
            null
        );
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class))).thenThrow(
            new IllegalStateException("Illegal transition for connection 7")
        );

        assertThatThrownBy(() -> service.completeConnection(pending, completed, "alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Illegal transition");
    }

    @Test
    @DisplayName("completeConnection with null CredentialBundle skips credentials persistence")
    void complete_nullCredentials_noBlobStamped() {
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        ConnectFinalization.Completed completed = new ConnectFinalization.Completed("T1", null, null);
        when(connectionRepository.save(any(Connection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionService.transition(any(Connection.class), any(TransitionRequest.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Connection result = service.completeConnection(pending, completed, "alice");

        assertThat(result.getCredentialsAlg()).isNull();
        assertThat(result.getCredentialsEncrypted()).isNull();
        verify(connectionService).transition(eq(pending), any(TransitionRequest.class));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Connection newConnection(
        long id,
        long workspaceId,
        IntegrationKind kind,
        String instanceKey,
        IntegrationState state
    ) {
        Workspace ws = Mockito.mock(Workspace.class);
        Mockito.lenient().when(ws.getId()).thenReturn(workspaceId);
        ConnectionConfig cfg = switch (kind) {
            case GITHUB -> new ConnectionConfig.GitHubAppConfig(null, null, null, java.util.Set.of());
            case GITLAB -> new ConnectionConfig.GitLabConfig(
                "https://gitlab.com",
                null,
                null,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                java.util.Set.of()
            );
            case SLACK -> new ConnectionConfig.SlackConfig(null, null, null, null, java.util.Set.of());
            case OUTLINE -> new ConnectionConfig.OutlineConfig("https://app.getoutline.com", null, java.util.Set.of());
        };
        Connection c = new Connection(ws, kind, instanceKey, cfg);
        c.setState(state);
        try {
            Field idField = Connection.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(c, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return c;
    }
}

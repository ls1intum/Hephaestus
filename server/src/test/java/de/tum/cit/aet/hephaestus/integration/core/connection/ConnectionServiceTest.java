package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * State-machine contract for {@link ConnectionService#transition}. Exercises the legal
 * path (one audit row, state mutated), the rejection path (no audit row, state untouched),
 * the same-state no-op, and the UNINSTALLED credential purge. {@link IntegrationStateTest}
 * covers the pure {@code canTransitionTo} predicate; this test covers the service that
 * enforces it.
 */
@Tag("unit")
class ConnectionServiceTest extends BaseUnitTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ConnectionAuditRepository auditRepository;

    private CredentialBundleConverter credentialConverter;
    private ConnectionService service;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Real converter so the credential-purge case operates on a genuine AES-GCM blob,
        // not a mock stand-in.
        credentialConverter = new CredentialBundleConverter("a".repeat(32), "dev");
        service = new ConnectionService(connectionRepository, auditRepository, credentialConverter);
        workspace = new Workspace();
        workspace.setId(7L);
        // transition() returns the saved entity; echo it back so callers see the mutated row.
        Mockito.lenient()
            .when(connectionRepository.save(any(Connection.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void transition_legalPending_toActive_writesOneAuditRowAndMutatesState() {
        Connection connection = pendingConnection();

        Connection result = service.transition(
            connection,
            new TransitionRequest(
                IntegrationState.ACTIVE,
                "INSTALL_BIND",
                "GITHUB_WEBHOOK",
                "actor-1",
                "corr-1",
                "linked"
            )
        );

        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(result.getStateReason()).isEqualTo("linked");

        // Exactly one audit row, carrying the correct from/to and request metadata.
        ArgumentCaptor<ConnectionAudit> audit = ArgumentCaptor.forClass(ConnectionAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getFromState()).isEqualTo(IntegrationState.PENDING);
        assertThat(audit.getValue().getToState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(audit.getValue().getEventType()).isEqualTo("INSTALL_BIND");
        assertThat(audit.getValue().getCorrelationId()).isEqualTo("corr-1");
        verify(connectionRepository).save(connection);
    }

    @Test
    void transition_illegalUninstalledToActive_throwsAndWritesNoAuditRowAndKeepsState() {
        Connection connection = connectionInState(IntegrationState.UNINSTALLED);

        assertThatThrownBy(() ->
            service.transition(
                connection,
                new TransitionRequest(IntegrationState.ACTIVE, "REVIVE", "ADMIN", "actor-1", "corr-x", "nope")
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Illegal transition")
            .hasMessageContaining("UNINSTALLED")
            .hasMessageContaining("ACTIVE");

        assertThat(connection.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        verify(auditRepository, never()).save(any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void transition_slackOAuthReconnectFromUninstalled_writesAuditRowAndReactivates() {
        Connection connection = new Connection(
            workspace,
            IntegrationKind.SLACK,
            "T1",
            new ConnectionConfig.SlackConfig("T1", "Acme", null, null, null, Set.of())
        );
        connection.setState(IntegrationState.UNINSTALLED);

        Connection result = service.transition(
            connection,
            new TransitionRequest(IntegrationState.ACTIVE, "OAUTH_COMPLETE", "USER", "actor-1", "corr-x", "reconnected")
        );

        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(result.getStateReason()).isEqualTo("reconnected");

        ArgumentCaptor<ConnectionAudit> audit = ArgumentCaptor.forClass(ConnectionAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getFromState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(audit.getValue().getToState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(audit.getValue().getEventType()).isEqualTo("OAUTH_COMPLETE");
        verify(connectionRepository).save(connection);
    }

    @Test
    void transition_illegalSuspendedToPending_throwsAndWritesNoAuditRow() {
        Connection connection = connectionInState(IntegrationState.SUSPENDED);

        assertThatThrownBy(() ->
            service.transition(
                connection,
                new TransitionRequest(IntegrationState.PENDING, "REWIND", "ADMIN", "actor-1", "corr-y", "nope")
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SUSPENDED")
            .hasMessageContaining("PENDING");

        assertThat(connection.getState()).isEqualTo(IntegrationState.SUSPENDED);
        verify(auditRepository, never()).save(any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void transition_sameState_isNoOpReturnsSameInstanceNoAuditRow() {
        Connection connection = connectionInState(IntegrationState.ACTIVE);

        Connection result = service.transition(
            connection,
            new TransitionRequest(
                IntegrationState.ACTIVE,
                "INSTALL_BIND",
                "GITHUB_WEBHOOK",
                "actor-1",
                "corr-1",
                "again"
            )
        );

        // Same-state call returns the unchanged instance without touching audit or repo,
        // and (per the code) does NOT overwrite stateReason.
        assertThat(result).isSameAs(connection);
        assertThat(result.getState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(result.getStateReason()).isNull();
        verify(auditRepository, never()).save(any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void transition_toUninstalled_purgesCredentials() {
        Connection connection = connectionInState(IntegrationState.ACTIVE);
        connection.setCredentials(new BearerToken("ghp-secret", null), credentialConverter);
        assertThat(connection.getCredentialsEncrypted()).isNotNull();
        assertThat(connection.getCredentialsAlg()).isEqualTo(CredentialBundleConverter.ALGORITHM_TAG);

        Connection result = service.transition(
            connection,
            new TransitionRequest(
                IntegrationState.UNINSTALLED,
                "UNINSTALL",
                "GITHUB_WEBHOOK",
                "actor-1",
                "corr-9",
                "removed"
            )
        );

        assertThat(result.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(result.getCredentialsEncrypted()).isNull();
        assertThat(result.getCredentialsAlg()).isNull();
        // The transition that purged credentials is still audited.
        ArgumentCaptor<ConnectionAudit> audit = ArgumentCaptor.forClass(ConnectionAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getToState()).isEqualTo(IntegrationState.UNINSTALLED);
    }

    @Test
    void transition_duplicateCorrelationId_shortCircuitsWithoutMutatingState() {
        Connection connection = pendingConnection();
        // Conflicting INSERT on the idempotency index → the conflicting redelivery is dropped.
        when(auditRepository.save(any(ConnectionAudit.class))).thenThrow(
            new DataIntegrityViolationException("uq_connection_audit_idempotency")
        );

        Connection result = service.transition(
            connection,
            new TransitionRequest(
                IntegrationState.ACTIVE,
                "INSTALL_BIND",
                "GITHUB_WEBHOOK",
                "actor-1",
                "corr-dup",
                "linked"
            )
        );

        // Idempotent short-circuit: state is NOT advanced and the row is not saved.
        assertThat(result).isSameAs(connection);
        assertThat(result.getState()).isEqualTo(IntegrationState.PENDING);
        verify(connectionRepository, never()).save(any());
    }

    private Connection pendingConnection() {
        return connectionInState(IntegrationState.PENDING);
    }

    private Connection connectionInState(IntegrationState state) {
        Connection connection = new Connection(
            workspace,
            IntegrationKind.GITHUB,
            "100",
            new ConnectionConfig.GitHubAppConfig(100L, null, null, Set.of())
        );
        connection.setState(state);
        return connection;
    }
}

package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Erasure must not be preemptable. Wired against a real {@link ConnectionService} rather than a mock
 * of it, because the property under test is precisely that the service does not fence this caller —
 * a stubbed {@code transition} could assert nothing about that.
 */
class ConnectionPurgeContributorTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ConnectionAuditRepository auditRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SyncJobService syncJobService;

    /**
     * Purge goes through {@code transition}, which passes no revoke callback, so the revoke transaction
     * template is never exercised here — an unstubbed mock is enough to construct the service.
     */
    @Mock
    private PlatformTransactionManager transactionManager;

    private CredentialBundleConverter credentialConverter;
    private ConnectionService connectionService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        credentialConverter = new CredentialBundleConverter("a".repeat(32), "dev");
        connectionService = new ConnectionService(
            connectionRepository,
            auditRepository,
            credentialConverter,
            eventPublisher,
            syncJobService,
            transactionManager
        );
        Mockito.lenient()
            .when(connectionRepository.save(any(Connection.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
    }

    @Test
    void purge_scrubsCredentialsAndNeverConsultsTheSyncFence() throws Exception {
        Connection connection = activeConnectionWithCredentials();
        when(connectionRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(connection));

        new ConnectionPurgeContributor(connectionRepository, connectionService).deleteWorkspaceData(WORKSPACE_ID);

        assertThat(connection.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(connection.getCredentialsEncrypted()).isNull();
        // Erasure is not preemptable: it goes through transition(), which never fences on an in-flight
        // sync job — so an in-flight job cannot make this assertion's outcome differ.
        verify(syncJobService, never()).requestCancelForTeardown(anyLong());
    }

    @Test
    void purge_skipsAlreadyUninstalledConnections() throws Exception {
        Connection connection = activeConnectionWithCredentials();
        connection.setState(IntegrationState.UNINSTALLED);
        when(connectionRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(connection));

        new ConnectionPurgeContributor(connectionRepository, connectionService).deleteWorkspaceData(WORKSPACE_ID);

        verify(auditRepository, never()).save(any());
    }

    private Connection activeConnectionWithCredentials() throws Exception {
        Connection connection = new Connection(
            workspace,
            IntegrationKind.GITHUB,
            "100",
            new ConnectionConfig.GitHubAppConfig(100L, null, null, Set.of())
        );
        Field id = Connection.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(connection, 55L);
        connection.setState(IntegrationState.ACTIVE);
        connection.setCredentials(new BearerToken("ghp-secret", null), credentialConverter);
        // Lenient: the already-UNINSTALLED case is skipped before applyTransition re-reads the row.
        Mockito.lenient()
            .when(connectionRepository.findByIdAndWorkspaceId(55L, WORKSPACE_ID))
            .thenReturn(Optional.of(connection));
        return connection;
    }
}

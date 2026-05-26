package de.tum.cit.aet.hephaestus.integration.gitlab.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Decryption smoke test for the GitLab provider — proves the converter wiring works
 * end-to-end. Connection lookup branches (missing / non-ACTIVE / missing blob → empty)
 * are covered transitively by the parallel Slack/Outline/GitHub tests + the converter's
 * own contract tests; we only re-verify the happy-path decode here to keep the suite
 * compact.
 */
@DisplayName("GitlabCredentialProvider — unit")
class GitlabCredentialProviderTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    private CredentialBundleConverter converter;
    private GitlabCredentialProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new CredentialBundleConverter("0123456789abcdef0123456789abcdef", "dev");
        provider = new GitlabCredentialProvider(connectionService, converter);
    }

    @Test
    @DisplayName("ACTIVE Connection with encrypted PAT decrypts back to a BearerToken")
    void activeConnection_decryptsPat() {
        long workspaceId = 17L;
        Connection connection = newGitlabConnection(workspaceId);
        connection.setCredentials(new BearerToken("glpat-secret", null), converter);
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.GITLAB)).thenReturn(Optional.of(connection));

        Optional<CredentialBundle> resolved = provider.resolve(
            new IntegrationRef(IntegrationKind.GITLAB, workspaceId, "200")
        );

        assertThat(resolved).contains(new BearerToken("glpat-secret", null));
    }

    @Test
    @DisplayName("ACTIVE Connection with no credential blob returns empty (does not crash)")
    void activeConnection_noBlob_returnsEmpty() {
        long workspaceId = 17L;
        Connection connection = newGitlabConnection(workspaceId);
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.GITLAB)).thenReturn(Optional.of(connection));

        assertThat(provider.resolve(new IntegrationRef(IntegrationKind.GITLAB, workspaceId, "200"))).isEmpty();
    }

    @Test
    @DisplayName("Wrong-kind ref short-circuits without touching the repo")
    void wrongKindRef_returnsEmpty() {
        assertThat(provider.resolve(new IntegrationRef(IntegrationKind.GITHUB, 17L, "100"))).isEmpty();
        Mockito.verifyNoInteractions(connectionService);
    }

    private static Connection newGitlabConnection(long workspaceId) {
        Workspace ws = Mockito.mock(Workspace.class);
        Mockito.lenient().when(ws.getId()).thenReturn(workspaceId);
        return new Connection(
            ws,
            IntegrationKind.GITLAB,
            "200",
            new ConnectionConfig.GitLabConfig(
                "https://gitlab.example.com",
                200L,
                null,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                Set.of()
            )
        );
    }
}

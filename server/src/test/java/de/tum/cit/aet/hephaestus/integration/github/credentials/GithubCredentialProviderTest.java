package de.tum.cit.aet.hephaestus.integration.github.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.GithubAppCredential;
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
 * Focused unit coverage for the PAT decryption path that used to be a TODO stub. The
 * GitHub App branch (no per-row credential blob — just installation identity) is
 * covered transitively by the broader integration tests; we only add the new behaviour
 * here to keep the suite tight.
 */
@DisplayName("GithubCredentialProvider — unit")
class GithubCredentialProviderTest extends BaseUnitTest {

    @Mock private ConnectionService connectionService;
    @Mock private GitHubAppTokenService appTokenService;

    private CredentialBundleConverter converter;
    private GithubCredentialProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new CredentialBundleConverter("0123456789abcdef0123456789abcdef", "dev");
        lenient().when(appTokenService.getConfiguredAppId()).thenReturn(42L);
        provider = new GithubCredentialProvider(connectionService, converter, appTokenService);
    }

    @Test
    @DisplayName("ACTIVE GitHubPatConfig Connection with encrypted PAT decrypts back to BearerToken")
    void patConnection_decryptsToken() {
        long workspaceId = 17L;
        Workspace ws = Mockito.mock(Workspace.class);
        Connection connection = new Connection(
            ws, IntegrationKind.GITHUB, "pat",
            new ConnectionConfig.GitHubPatConfig(/* orgLogin */ "acme", null, Set.of())
        );
        connection.setCredentials(new BearerToken("ghp_secretToken", null), converter);
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.GITHUB))
            .thenReturn(Optional.of(connection));

        Optional<CredentialBundle> resolved = provider.resolve(
            new IntegrationRef(IntegrationKind.GITHUB, workspaceId, "pat"));

        assertThat(resolved).contains(new BearerToken("ghp_secretToken", null));
    }

    @Test
    @DisplayName("ACTIVE GitHubAppConfig Connection still surfaces installation identity (regression guard)")
    void appConnection_surfacesGithubAppCredential() {
        long workspaceId = 17L;
        Workspace ws = Mockito.mock(Workspace.class);
        Connection connection = new Connection(
            ws, IntegrationKind.GITHUB, "100",
            new ConnectionConfig.GitHubAppConfig(100L, "acme", null, Set.of())
        );
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.GITHUB))
            .thenReturn(Optional.of(connection));

        Optional<CredentialBundle> resolved = provider.resolve(
            new IntegrationRef(IntegrationKind.GITHUB, workspaceId, "100"));

        assertThat(resolved).hasValueSatisfying(bundle -> {
            assertThat(bundle).isInstanceOf(GithubAppCredential.class);
            GithubAppCredential gh = (GithubAppCredential) bundle;
            assertThat(gh.installationId()).isEqualTo(100L);
        });
    }

    @Test
    @DisplayName("PAT Connection with no credential blob returns empty rather than throwing")
    void patConnection_noBlob_returnsEmpty() {
        long workspaceId = 17L;
        Workspace ws = Mockito.mock(Workspace.class);
        Connection connection = new Connection(
            ws, IntegrationKind.GITHUB, "pat",
            new ConnectionConfig.GitHubPatConfig(null, null, Set.of())
        );
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.GITHUB))
            .thenReturn(Optional.of(connection));

        assertThat(provider.resolve(new IntegrationRef(IntegrationKind.GITHUB, workspaceId, "pat")))
            .isEmpty();
    }
}

package de.tum.cit.aet.hephaestus.integration.scm.github.connect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GithubConnectionStrategyTest extends BaseUnitTest {

    @Mock
    private OAuthStateService oauthStateService;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private Connection connection;

    @Mock
    private GitHubAppTokenService appTokenService;

    @Mock
    private ScmWorkspaceContentEraser contentEraser;

    private GithubConnectionStrategy strategy() {
        return new GithubConnectionStrategy(
            "https://github.com/apps/heph/installations/new",
            "123",
            oauthStateService,
            connectionService,
            appTokenService,
            contentEraser
        );
    }

    @Test
    void initiate_weavesInitiatingAdminActorRefIntoTheOAuthState() {
        when(oauthStateService.issue(7L, IntegrationKind.GITHUB, "admin@example.com")).thenReturn("state-xyz");

        strategy().initiate(
            new ConnectionStrategy.InitiateRequest(7L, IntegrationKind.GITHUB, Map.of(), "admin@example.com")
        );

        verify(oauthStateService).issue(7L, IntegrationKind.GITHUB, "admin@example.com");
    }

    @Test
    void revoke_uninstallsTheGitHubAppAndErasesTheLocalMirror() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242");
        when(connectionService.findReferenced(ref)).thenReturn(Optional.of(connection));
        when(connection.getConfig()).thenReturn(new ConnectionConfig.GitHubAppConfig(4242L, null, null, Set.of()));

        strategy().revoke(ref);

        verify(appTokenService).deleteInstallation(4242L);
        verify(contentEraser).eraseWorkspaceScmMirror(7L);
    }

    @Test
    void revoke_patConnectionNeverDeletesAnInstallation() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242");
        when(connectionService.findReferenced(ref)).thenReturn(Optional.of(connection));
        when(connection.getConfig()).thenReturn(new ConnectionConfig.GitHubPatConfig("org", null, Set.of()));

        strategy().revoke(ref);

        verifyNoInteractions(appTokenService);
        verify(contentEraser).eraseWorkspaceScmMirror(7L);
    }

    @Test
    void purge_revokesProviderWithoutErasingLocalData() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242", 9L);
        when(connectionService.findReferenced(ref)).thenReturn(Optional.of(connection));
        when(connection.getConfig()).thenReturn(new ConnectionConfig.GitHubAppConfig(4242L, null, null, Set.of()));

        strategy().revokeProvider(ref);

        verify(appTokenService).deleteInstallation(4242L);
        verifyNoInteractions(contentEraser);
    }

    @Test
    void purge_doesNotDeleteAnInstallationStillUsedByAnotherConnection() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242", 9L);
        when(connectionService.hasOtherInstalledConnection(ref)).thenReturn(true);

        strategy().revokeProvider(ref);

        verifyNoInteractions(appTokenService, contentEraser);
    }

    @Test
    void purge_propagatesProviderFailureWithoutErasingLocalData() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242", 9L);
        when(connectionService.findReferenced(ref)).thenReturn(Optional.of(connection));
        when(connection.getConfig()).thenReturn(new ConnectionConfig.GitHubAppConfig(4242L, null, null, Set.of()));
        doThrow(new RuntimeException("github unavailable")).when(appTokenService).deleteInstallation(4242L);

        assertThatThrownBy(() -> strategy().revokeProvider(ref)).hasMessage("github unavailable");

        verifyNoInteractions(contentEraser);
    }

    @Test
    void revoke_erasesLocallyWhenGitHubUninstallFails() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242");
        when(connectionService.findReferenced(ref)).thenReturn(Optional.of(connection));
        when(connection.getConfig()).thenReturn(new ConnectionConfig.GitHubAppConfig(4242L, null, null, Set.of()));
        doThrow(new RuntimeException("github unavailable")).when(appTokenService).deleteInstallation(4242L);

        assertThatThrownBy(() -> strategy().revoke(ref)).isInstanceOf(RuntimeException.class);

        verify(contentEraser).eraseWorkspaceScmMirror(7L);
    }

    @Test
    void revoke_withNullRef_isANoOp() {
        strategy().revoke(null);

        verifyNoInteractions(connectionService, appTokenService, contentEraser);
    }
}

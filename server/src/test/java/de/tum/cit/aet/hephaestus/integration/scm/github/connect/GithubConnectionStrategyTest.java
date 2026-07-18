package de.tum.cit.aet.hephaestus.integration.scm.github.connect;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Pins the attribution fix (75e51c1a5) on the GitHub side: the initiating admin's actorRef must be woven
 * into the OAuth state via the 3-arg issue(), so the post-callback connection audit row attributes the
 * connect to them. The Slack side is covered by SlackConnectionStrategyTest; the GitHub controller test
 * passes a null authentication, so a re-dropped actorRef would otherwise slip through unnoticed.
 *
 * <p>Also pins the F2 disconnect-erase wiring: {@code revoke} must drive the shared SCM eraser, not the
 * old no-op log. Without this the admin-disconnect trigger silently regresses to "keeps the mirror
 * forever" while the vendor-uninstall trigger still erases — the exact asymmetry F2 closed.
 */
class GithubConnectionStrategyTest extends BaseUnitTest {

    @Mock
    private OAuthStateService oauthStateService;

    @Mock
    private ScmWorkspaceContentEraser contentEraser;

    private GithubConnectionStrategy strategy() {
        return new GithubConnectionStrategy(
            "https://github.com/apps/heph/installations/new",
            "123",
            oauthStateService,
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
    void revoke_erasesTheWorkspacesScmMirror() {
        strategy().revoke(new IntegrationRef(IntegrationKind.GITHUB, 7L, "4242"));

        verify(contentEraser).eraseWorkspaceScmMirror(7L);
    }

    @Test
    void revoke_withNullRef_isANoOp() {
        strategy().revoke(null);

        verifyNoInteractions(contentEraser);
    }
}

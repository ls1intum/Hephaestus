package de.tum.cit.aet.hephaestus.integration.scm.github.connect;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Pins the attribution fix (75e51c1a5) on the GitHub side: the initiating admin's actorRef must be woven
 * into the OAuth state via the 3-arg issue(), so the post-callback connection audit row attributes the
 * connect to them. The Slack side is covered by SlackConnectionStrategyTest; the GitHub controller test
 * passes a null authentication, so a re-dropped actorRef would otherwise slip through unnoticed.
 */
class GithubConnectionStrategyTest extends BaseUnitTest {

    @Mock
    private OAuthStateService oauthStateService;

    @Test
    void initiate_weavesInitiatingAdminActorRefIntoTheOAuthState() {
        when(oauthStateService.issue(7L, IntegrationKind.GITHUB, "admin@example.com")).thenReturn("state-xyz");
        GithubConnectionStrategy strategy = new GithubConnectionStrategy(
            "https://github.com/apps/heph/installations/new",
            "123",
            oauthStateService
        );

        strategy.initiate(
            new ConnectionStrategy.InitiateRequest(7L, IntegrationKind.GITHUB, Map.of(), "admin@example.com")
        );

        verify(oauthStateService).issue(7L, IntegrationKind.GITHUB, "admin@example.com");
    }
}

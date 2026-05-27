package de.tum.cit.aet.hephaestus.integration.slack.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.ConnectFinalization;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.connect.SlackOAuthClient.OAuthV2Access;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SlackConnectionStrategyTest extends BaseUnitTest {

    @Mock
    private OAuthStateService oauthStateService;

    @Mock
    private SlackOAuthClient oauthClient;

    @Mock
    private SlackCredentialProvider credentialProvider;

    private SlackConnectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SlackConnectionStrategy(
            oauthStateService,
            oauthClient,
            credentialProvider,
            "client-id",
            "https://app.test/oauth/callback/slack"
        );
    }

    private static IntegrationRef ref() {
        return new IntegrationRef(IntegrationKind.SLACK, 42L, null);
    }

    @Test
    void finalize_oauthClientThrows_returnsFailedWithErrorMessage() {
        when(oauthClient.exchangeCode(eq("c1"), any())).thenThrow(new SlackOAuthException("invalid_code"));

        ConnectFinalization r = strategy.finalizeConnect(ref(), Map.of("code", "c1"));

        assertThat(r).isInstanceOf(ConnectFinalization.Failed.class);
        assertThat(((ConnectFinalization.Failed) r).reason()).contains("invalid_code");
    }

    @Test
    void finalize_rotationFields_rejectsWithExplicitMessage() {
        when(oauthClient.exchangeCode(eq("c"), any())).thenReturn(
            new OAuthV2Access(
                true,
                null,
                "xoxe-abc",
                "U1",
                "A1",
                new OAuthV2Access.Team("T1", "Acme"),
                "chat:write",
                /* expiresIn */ 43200,
                /* refreshToken */ "xoxe-refresh"
            )
        );

        ConnectFinalization r = strategy.finalizeConnect(ref(), Map.of("code", "c"));

        assertThat(r).isInstanceOf(ConnectFinalization.Failed.class);
        assertThat(((ConnectFinalization.Failed) r).reason()).contains("Token rotation");
    }

    @Test
    void finalize_missingTeam_returnsFailed() {
        when(oauthClient.exchangeCode(eq("c"), any())).thenReturn(
            new OAuthV2Access(true, null, "xoxb", "U1", "A1", null, "chat:write", null, null)
        );

        ConnectFinalization r = strategy.finalizeConnect(ref(), Map.of("code", "c"));

        assertThat(r).isInstanceOf(ConnectFinalization.Failed.class);
        assertThat(((ConnectFinalization.Failed) r).reason()).contains("missing team");
    }

    @Test
    void finalize_happyPath_returnsCompletedWithTeamConfigAndBearerToken() {
        when(oauthClient.exchangeCode(eq("c"), eq("https://app.test/oauth/callback/slack"))).thenReturn(
            new OAuthV2Access(
                true,
                null,
                "xoxb-abc",
                "U1",
                "A1",
                new OAuthV2Access.Team("T9", "Hephaestus Test"),
                "chat:write,team:read",
                null,
                null
            )
        );

        ConnectFinalization r = strategy.finalizeConnect(ref(), Map.of("code", "c"));

        assertThat(r).isInstanceOf(ConnectFinalization.Completed.class);
        ConnectFinalization.Completed c = (ConnectFinalization.Completed) r;
        assertThat(c.instanceKey()).isEqualTo("T9");
        assertThat(c.displayName()).isEqualTo("Hephaestus Test");
        assertThat(c.credentials()).isInstanceOf(BearerToken.class);
        assertThat(((BearerToken) c.credentials()).token()).isEqualTo("xoxb-abc");
        assertThat(c.config()).isInstanceOf(ConnectionConfig.SlackConfig.class);
        ConnectionConfig.SlackConfig cfg = (ConnectionConfig.SlackConfig) c.config();
        assertThat(cfg.teamId()).isEqualTo("T9");
        assertThat(cfg.teamName()).isEqualTo("Hephaestus Test");
        assertThat(cfg.notificationChannelId()).isNull();
        assertThat(cfg.teamLabel()).isNull();
    }

    @Test
    void initiate_buildsAuthorizeUrlWithLockedScopes() {
        when(oauthStateService.issue(42L, IntegrationKind.SLACK)).thenReturn("state-abc");

        var initiation = strategy.initiate(
            new de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.InitiateRequest(
                42L,
                IntegrationKind.SLACK,
                Map.of(),
                null
            )
        );

        assertThat(initiation).isInstanceOf(
            de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.ConnectInitiation.RedirectToVendor.class
        );
        var redirect =
            (de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.ConnectInitiation.RedirectToVendor) initiation;
        String url = redirect.vendorUrl().toString();
        assertThat(url).startsWith("https://slack.com/oauth/v2/authorize?");
        assertThat(url).contains("chat%3Awrite");
        assertThat(url).contains("chat%3Awrite.public");
        assertThat(url).contains("team%3Aread");
        assertThat(url).contains("users%3Aread");
        assertThat(url).contains("users%3Aread.email");
        assertThat(url).contains("state=state-abc");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fapp.test%2Foauth%2Fcallback%2Fslack");
    }
}

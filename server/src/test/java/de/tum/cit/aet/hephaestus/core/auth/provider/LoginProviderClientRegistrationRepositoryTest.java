package de.tum.cit.aet.hephaestus.core.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * nOAuth linchpin: every login {@link ClientRegistration} built from a {@code login_provider} row MUST
 * key its principal on an IdP-stable, immutable subject ({@code id}/{@code sub}) — never a mutable
 * handle (a renameable GitLab {@code username}, or an {@code email} an attacker can set on a controlled
 * IdP). {@code IdentityLink.subject} is keyed on this; a regression is the classic nOAuth takeover.
 * Also checks the GitHub vs GitLab endpoint wiring.
 */
class LoginProviderClientRegistrationRepositoryTest extends BaseUnitTest {

    private static final Set<String> STABLE_SUBJECT_ATTRIBUTES = Set.of("id", "sub");

    private static LoginProvider provider(
        String registrationId,
        LoginProvider.ProviderType type,
        String baseUrl,
        String scopes
    ) {
        LoginProvider p = new LoginProvider();
        p.setRegistrationId(registrationId);
        p.setType(type);
        p.setDisplayName(registrationId);
        p.setBaseUrl(baseUrl);
        p.setClientId("client-id");
        p.setClientSecret("client-secret");
        p.setScopes(scopes);
        p.setEnabled(true);
        return p;
    }

    @Test
    void everyRegistration_usesStableSubjectAttribute() {
        LoginProviderRepository repo = mock(LoginProviderRepository.class);
        when(repo.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(
            List.of(
                provider("github", LoginProvider.ProviderType.GITHUB, "https://github.com", "read:user user:email"),
                provider("gitlab-lrz", LoginProvider.ProviderType.GITLAB, "https://gitlab.lrz.de", "openid profile")
            )
        );

        List<ClientRegistration> registrations = new LoginProviderClientRegistrationRepository(
            repo,
            ""
        ).listRegistrations();

        assertThat(registrations).hasSize(2);
        for (ClientRegistration registration : registrations) {
            assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .as(
                    "'%s' must key the principal on an IdP-stable subject (nOAuth defence)",
                    registration.getRegistrationId()
                )
                .isIn(STABLE_SUBJECT_ATTRIBUTES);
        }
    }

    @Test
    void slackIsAnOidcProviderKeyedOnSubAndDiscoverableForAccountLinking() {
        LoginProviderRepository repo = mock(LoginProviderRepository.class);
        when(repo.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(
            List.of(
                provider("github", LoginProvider.ProviderType.GITHUB, "https://github.com", "read:user user:email"),
                provider("slack", LoginProvider.ProviderType.SLACK, "https://slack.com", "openid profile email")
            )
        );
        when(repo.findByRegistrationId("slack")).thenReturn(
            Optional.of(
                provider("slack", LoginProvider.ProviderType.SLACK, "https://slack.com", "openid profile email")
            )
        );

        LoginProviderClientRegistrationRepository repository = new LoginProviderClientRegistrationRepository(repo, "");

        // Link-only in the SPA, but discoverable so the authenticated settings page can offer account linking.
        List<ClientRegistration> picker = repository.listRegistrations();
        assertThat(picker).extracting(ClientRegistration::getRegistrationId).containsExactly("github", "slack");

        // But it IS reachable by registrationId for the account-linking flow, wired to Slack's OIDC endpoints.
        ClientRegistration slack = repository.findByRegistrationId("slack");
        assertThat(slack.getProviderDetails().getAuthorizationUri()).isEqualTo(
            "https://slack.com/openid/connect/authorize"
        );
        assertThat(slack.getProviderDetails().getTokenUri()).isEqualTo("https://slack.com/api/openid.connect.token");
        assertThat(slack.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo(
            "https://slack.com/api/openid.connect.userInfo"
        );
        assertThat(slack.getProviderDetails().getJwkSetUri()).isEqualTo("https://slack.com/openid/connect/keys");
        assertThat(slack.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
    }

    @Test
    void outlineIsPlainOauth2KeyedOnIdWithAuthInfoUserinfo() {
        LoginProviderRepository repo = mock(LoginProviderRepository.class);
        when(repo.findByRegistrationId("outline")).thenReturn(
            Optional.of(provider("outline", LoginProvider.ProviderType.OUTLINE, "https://wiki.example.com", "read"))
        );

        ClientRegistration outline = new LoginProviderClientRegistrationRepository(repo, "").findByRegistrationId(
            "outline"
        );

        // Plain OAuth2 (NOT OIDC): endpoints hang off the per-instance base URL; the userinfo URI points
        // at POST /api/auth.info, which OutlineAuthInfoUserService (not the framework default) calls.
        assertThat(outline.getProviderDetails().getAuthorizationUri()).isEqualTo(
            "https://wiki.example.com/oauth/authorize"
        );
        assertThat(outline.getProviderDetails().getTokenUri()).isEqualTo("https://wiki.example.com/oauth/token");
        assertThat(outline.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo(
            "https://wiki.example.com/api/auth.info"
        );
        assertThat(outline.getProviderDetails().getJwkSetUri()).isNull(); // no OIDC path
        // nOAuth defence: the principal keys on the immutable Outline user UUID, never name/email.
        assertThat(outline.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
    }

    @Test
    void gitlabEndpointsHangOffTheInstanceBaseUrl() {
        LoginProviderRepository repo = mock(LoginProviderRepository.class);
        when(repo.findByRegistrationId("gitlab-lrz")).thenReturn(
            Optional.of(provider("gitlab-lrz", LoginProvider.ProviderType.GITLAB, "https://gitlab.lrz.de", "openid"))
        );

        ClientRegistration reg = new LoginProviderClientRegistrationRepository(repo, "").findByRegistrationId(
            "gitlab-lrz"
        );

        assertThat(reg.getProviderDetails().getAuthorizationUri()).isEqualTo("https://gitlab.lrz.de/oauth/authorize");
        assertThat(reg.getProviderDetails().getTokenUri()).isEqualTo("https://gitlab.lrz.de/oauth/token");
        assertThat(reg.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo(
            "https://gitlab.lrz.de/api/v4/user"
        );
    }

    @Test
    void redirectUri_carriesTheConfiguredApiBasePath() {
        LoginProviderRepository repo = mock(LoginProviderRepository.class);
        when(repo.findByRegistrationId("github")).thenReturn(
            Optional.of(provider("github", LoginProvider.ProviderType.GITHUB, "https://github.com", "read:user"))
        );

        // Behind a proxy that strips /api, the redirect_uri the IdP gets must re-add it so the callback
        // lands on the proxied API path, not the SPA. {baseUrl} is expanded by Spring at request time.
        ClientRegistration prefixed = new LoginProviderClientRegistrationRepository(repo, "/api").findByRegistrationId(
            "github"
        );
        assertThat(prefixed.getRedirectUri()).isEqualTo("{baseUrl}/api/login/oauth2/code/{registrationId}");

        ClientRegistration root = new LoginProviderClientRegistrationRepository(repo, "").findByRegistrationId(
            "github"
        );
        assertThat(root.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    }
}

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

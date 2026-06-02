package de.tum.cit.aet.hephaestus.core.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.spi.OAuthLoginDefaultsProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * nOAuth linchpin: every default login {@link ClientRegistration} MUST resolve its principal
 * name from an IdP-stable, immutable subject claim ({@code id} or {@code sub}) — NOT a mutable
 * handle like {@code username} or {@code email}.
 *
 * <p>{@code IdentityLink.subject} is keyed on this attribute. If a registration used a mutable
 * value (e.g. a GitLab {@code username}, which the owner can rename, or an {@code email}, which
 * an attacker may set to a victim's address on a controlled IdP), a future login could be
 * matched onto someone else's existing IdentityLink — the classic nOAuth account-takeover.
 *
 * <p>This test builds the env-default registrations through the real
 * {@link AuthSecurityConfig#oauthLoginDefaultsProvider(AuthProperties)} bean (both providers
 * configured) and fails if any {@code userNameAttributeName} regresses away from {@code id}/
 * {@code sub} — e.g. reverting {@code gitlab-lrz} to {@code "username"}.
 */
class OAuthLoginRegistrationSubjectTest extends BaseUnitTest {

    private static final Set<String> STABLE_SUBJECT_ATTRIBUTES = Set.of("id", "sub");

    @Test
    void everyDefaultLoginRegistration_usesStableSubjectAttribute() {
        AuthProperties properties = fullyConfiguredAuthProperties();

        OAuthLoginDefaultsProvider provider = new AuthSecurityConfig().oauthLoginDefaultsProvider(properties);
        List<ClientRegistration> registrations = provider.defaultRegistrations();

        // Sanity: both env-default providers were actually built (otherwise the assertion below
        // would vacuously pass on an empty list and silently stop guarding the contract).
        assertThat(registrations).hasSize(2);

        for (ClientRegistration registration : registrations) {
            String userNameAttribute = registration
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
            assertThat(userNameAttribute)
                .as(
                    "ClientRegistration '%s' must key the principal on an IdP-stable subject " +
                        "(id/sub) for the nOAuth defence, not a mutable handle",
                    registration.getRegistrationId()
                )
                .isIn(STABLE_SUBJECT_ATTRIBUTES);
        }
    }

    private static AuthProperties fullyConfiguredAuthProperties() {
        return new AuthProperties(
            URI.create("http://localhost:38080"),
            "hephaestus-spa",
            Duration.ofMinutes(15),
            "__Host-HEPHAESTUS_AT",
            "",
            Duration.ofHours(48),
            new AuthProperties.GithubLogin("gh-client-id", "gh-client-secret"),
            new AuthProperties.GitlabLrzLogin("gl-client-id", "gl-client-secret", URI.create("https://gitlab.lrz.de"))
        );
    }
}

package de.tum.cit.aet.hephaestus.core.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Unit coverage for the OAuth-registration → discovery mapping in {@link IdentityProviderDiscoveryController}:
 * {@code providerTypeOf} (GITHUB vs GITLAB vs SLACK host classification) and {@code baseUrlOf} (scheme://host[:port]
 * reconstruction incl. the malformed/host-less fallback). These are the genuinely error-prone branches — host
 * (not substring) matching and port reconstruction — that the integration tests never assert.
 */
class IdentityProviderDiscoveryControllerTest extends BaseUnitTest {

    private static ClientRegistration registration(String authorizationUri) {
        return ClientRegistration.withRegistrationId("p")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/p")
            .authorizationUri(authorizationUri)
            .tokenUri("https://example.com/token")
            .build();
    }

    @Test
    @DisplayName("github.com authorization host classifies as GITHUB")
    void githubHostIsGithub() {
        ClientRegistration reg = registration("https://github.com/login/oauth/authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("GITHUB");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEqualTo("https://github.com");
    }

    @Test
    @DisplayName("self-hosted GitLab on a non-default port classifies as GITLAB and keeps the port in baseUrl")
    void selfHostedGitlabWithPort() {
        ClientRegistration reg = registration("https://gitlab.example.com:8443/oauth/authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("GITLAB");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEqualTo("https://gitlab.example.com:8443");
    }

    @Test
    @DisplayName("GitLab.com (default port) classifies as GITLAB with a port-less origin")
    void gitlabDotComDefaultPort() {
        ClientRegistration reg = registration("https://gitlab.com/oauth/authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("GITLAB");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEqualTo("https://gitlab.com");
    }

    @Test
    @DisplayName("slack.com authorization host classifies as SLACK")
    void slackHostIsSlack() {
        ClientRegistration reg = registration("https://slack.com/openid/connect/authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("SLACK");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEqualTo("https://slack.com");
    }

    @Test
    @DisplayName("'github.com' appearing in a GitLab host's PATH does not misclassify as GITHUB (host, not substring)")
    void githubComInPathStaysGitlab() {
        ClientRegistration reg = registration("https://gitlab.internal/github.com/oauth/authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("GITLAB");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEqualTo("https://gitlab.internal");
    }

    @Test
    @DisplayName("a host-less (opaque) authorization URI falls back to GITLAB and an empty baseUrl")
    void hostlessUriFallsBackToGitlabAndEmptyBaseUrl() {
        // An opaque URI parses (no URISyntaxException) but has a null host — exercises the null-host fallback in
        // both helpers without depending on the builder accepting a null authorizationUri.
        ClientRegistration reg = registration("urn:example:authorize");
        assertThat(IdentityProviderDiscoveryController.providerTypeOf(reg)).isEqualTo("GITLAB");
        assertThat(IdentityProviderDiscoveryController.baseUrlOf(reg)).isEmpty();
    }
}

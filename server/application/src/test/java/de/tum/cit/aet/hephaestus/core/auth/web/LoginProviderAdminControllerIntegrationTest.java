package de.tum.cit.aet.hephaestus.core.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Access-control + happy-path for {@code /admin/login-providers}: only an instance admin
 * ({@code app_admin}) may manage login providers, the create response carries the upstream redirect
 * URI, and the sealed client secret is never returned.
 */
class LoginProviderAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithUser
    void nonAdminCannotListLoginProviders() {
        webTestClient
            .get()
            .uri("/admin/login-providers")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithUser
    void nonAdminCannotMutateLoginProviders() {
        // The app_admin gate must guard the destructive endpoints too, not just the list — it fires
        // before any provider lookup, so the result is 403 regardless of whether "github" exists.
        webTestClient
            .patch()
            .uri("/admin/login-providers/github")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("enabled", false))
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .delete()
            .uri("/admin/login-providers/github")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithAdminUser
    void adminCanCreateAndListWithoutLeakingTheSecret() {
        Map<String, Object> body = Map.of(
            "registrationId",
            "gitlab-actest",
            "type",
            "GITLAB",
            "displayName",
            "ACME GitLab",
            "baseUrl",
            "https://gitlab.acme.test",
            "clientId",
            "acme-client-id",
            "clientSecret",
            "super-secret-value",
            "scopes",
            "read_user"
        );

        webTestClient
            .post()
            .uri("/admin/login-providers")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location", location -> assertThat(location).endsWith("/admin/login-providers/gitlab-actest"))
            .expectBody()
            .jsonPath("$.registrationId")
            .isEqualTo("gitlab-actest")
            .jsonPath("$.baseUrl")
            .isEqualTo("https://gitlab.acme.test")
            .jsonPath("$.redirectUri")
            .value(uri -> assertThat((String) uri).endsWith("/login/oauth2/code/gitlab-actest"))
            .jsonPath("$.clientSecret")
            .doesNotExist();

        webTestClient
            .get()
            .uri("/admin/login-providers")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[?(@.registrationId == 'gitlab-actest')]")
            .exists();
    }
}

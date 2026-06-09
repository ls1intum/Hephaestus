package de.tum.cit.aet.hephaestus.core.auth.dev;

import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The default (disabled) contract: the dev sign-in is not usable — with the flag off, neither the
 * permit rule nor the CSRF carve-out is registered, so the cookieless state-changing
 * {@code POST /auth/dev-login} is rejected 403 by CSRF before it can reach the controller, and sets no
 * cookie; discovery omits the {@code dev} row. This also proves the carve-out is flag-gated (it would
 * be 204 if the skip leaked). No {@code dev-login-enabled} property is set, so default {@code false} applies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@org.springframework.context.annotation.Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class DevLoginDisabledIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @Test
    void devLoginIsRejectedAndSetsNoCookieWhenDisabled() {
        webTestClient
            .post()
            .uri("/auth/dev-login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"alice\",\"admin\":true}")
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectCookie()
            .doesNotExist("__Host-HEPHAESTUS_AT");
    }

    @Test
    void discoveryOmitsTheDevRowWhenDisabled() {
        webTestClient
            .get()
            .uri("/identity-providers")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[?(@.registrationId == 'dev')]")
            .doesNotExist();
    }
}

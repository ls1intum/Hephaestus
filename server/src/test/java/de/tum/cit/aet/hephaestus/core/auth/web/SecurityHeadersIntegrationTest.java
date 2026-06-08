package de.tum.cit.aet.hephaestus.core.auth.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pins that {@code core.security.SecurityHeaders} is actually applied on the live security chain.
 * {@code SecurityHeaders.apply} is the single source of truth wired into the resource-server, the
 * oauth2Login and the lockdown chains — but nothing asserted the headers reach the wire, so dropping
 * {@code .apply()} from a chain or a Spring-default change would have been caught only by inspection.
 * Asserted against the public {@code GET /identity-providers} so no auth/CSRF is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class SecurityHeadersIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @Autowired
    private JwtSigningKeyService signingKeyService;

    @BeforeEach
    void cleanSlate() {
        databaseTestUtils.cleanDatabase();
        signingKeyService.ensureActiveKey();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @Test
    void publicResponseCarriesTheHardenedSecurityHeaders() {
        // Strict-Transport-Security is intentionally NOT asserted here: Spring's HstsHeaderWriter only
        // emits it over HTTPS (requireSecure), and WebTestClient drives the app over plain HTTP. The
        // remaining hardening headers are protocol-independent and must always be present.
        webTestClient
            .get()
            .uri("/identity-providers")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader()
            .valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
            .expectHeader()
            .valueEquals("Cross-Origin-Opener-Policy", "same-origin")
            .expectHeader()
            .valueEquals("Cross-Origin-Embedder-Policy", "credentialless")
            // CSP is ENFORCED (not report-only) and INSTANCE-AGNOSTIC. Pin the load-bearing directives,
            // the no-host posture, and the absence of a plaintext-HTTP image downgrade. `script-src 'self';`
            // (trailing ';') proves no source was appended (no 'unsafe-inline'/host).
            .expectHeader()
            .value(
                "Content-Security-Policy",
                allOf(
                    containsString("default-src 'self'"),
                    containsString("script-src 'self';"),
                    containsString("img-src 'self' data: https:"),
                    containsString("form-action 'self'"),
                    containsString("frame-ancestors 'none'"),
                    containsString("base-uri 'self'"),
                    // Regression guards: no instance-specific host, and no plaintext-HTTP image downgrade
                    // ('http:' is not a substring of 'https:').
                    not(containsString("gitlab")),
                    not(containsString("github")),
                    not(containsString("http:"))
                )
            )
            // A future accidental re-introduction of reportOnly() must fail this test.
            .expectHeader()
            .doesNotExist("Content-Security-Policy-Report-Only");
    }
}

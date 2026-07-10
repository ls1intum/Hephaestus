package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves StaleAuthCookieFilter: a logged-out browser still presenting an INVALID access cookie must
 * not be 401'd on a public endpoint (or the login page can't load its sign-in options — the symptom
 * this filter exists to kill), while a protected endpoint still 401s and a VALID cookie is untouched.
 *
 * <p>Runs over the LIVE security chain (real RevocationAwareJwtDecoder + real ES256 cookie-JWT), the
 * same setup as {@code SessionRefreshLifecycleIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class StaleAuthCookieEvictionIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HephaestusJwtIssuer jwtIssuer;

    @Autowired
    private JwtPrincipalFactory principalFactory;

    @Value("${hephaestus.auth.cookie-name:__Host-HEPHAESTUS_AT}")
    private String cookieName;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        var postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }

    @Test
    void staleCookieDoesNotBlockPublicEndpointAndIsCleared() {
        // A logged-out browser still holding a structurally-invalid cookie (e.g. signed by a key that
        // rotated across a restart, or simply garbage) hits the public sign-in discovery endpoint.
        var result = webTestClient
            .get()
            .uri("/identity-providers")
            .header(HttpHeaders.COOKIE, cookieName + "=not-a-valid-jwt")
            .exchange()
            // Served, NOT 401: the permitAll endpoint is reachable so the login page renders.
            .expectStatus()
            .isOk()
            .returnResult(Void.class);

        // The dead cookie is cleared so the browser stops resending it — self-healing. A clear is an
        // empty value with a non-positive Max-Age (Tomcat may serialise it as Max-Age=0 or as a past
        // Expires, surfaced as Duration.ZERO or -1s respectively — both mean "drop it now").
        ResponseCookie cleared = result.getResponseCookies().getFirst(cookieName);
        assertThat(cleared).as("stale cookie must be cleared").isNotNull();
        assertThat(cleared.getValue()).as("clearing cookie has an empty value").isEmpty();
        assertThat(cleared.getMaxAge())
            .as("clearing cookie is not kept alive")
            .isLessThanOrEqualTo(java.time.Duration.ZERO);
    }

    @Test
    void staleCookieOnProtectedEndpointStill401sAsUnauthenticated() {
        // The eviction must NOT silently authenticate: a protected endpoint still rejects the request
        // (401 = the correct "logged out" signal the SPA expects, not a 500 bad-token error).
        webTestClient
            .get()
            .uri("/user")
            .header(HttpHeaders.COOKIE, cookieName + "=not-a-valid-jwt")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void validCookieIsUntouchedAndStillAuthenticates() {
        Account account = accountRepository.save(new Account("Valid Vera"));
        String token = jwtIssuer.issue(principalFactory.forAccount(account), null, null).value();

        var result = webTestClient
            .get()
            .uri("/user")
            .header(HttpHeaders.COOKIE, cookieName + "=" + token)
            // A locally-valid cookie passes the filter untouched and authenticates normally.
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Void.class);

        // The filter must NOT clear a valid cookie.
        assertThat(result.getResponseCookies().getFirst(cookieName))
            .as("a valid cookie is left untouched (no clearing Set-Cookie)")
            .isNull();
    }
}

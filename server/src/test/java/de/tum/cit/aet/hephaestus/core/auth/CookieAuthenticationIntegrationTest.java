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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the cookie-session bearer-token resolution wired on the resource-server chain (ADR 0017).
 *
 * <p>The SPA authenticates by an HttpOnly {@code __Host-HEPHAESTUS_AT} cookie and never sends an
 * {@code Authorization} header. The framework default resolver reads ONLY the header, so this test
 * FAILS on the previous (header-only) wiring: a request carrying just the cookie is rejected 401 and
 * {@code GET /user} never returns the account.
 *
 * <p>Unlike most integration tests this one deliberately uses the <b>real</b>
 * {@code RevocationAwareJwtDecoder} (it does NOT import {@code TestSecurityConfig}'s mock decoder)
 * and mints a genuine ES256 cookie-JWT through {@link HephaestusJwtIssuer}, so the full
 * resolver → decoder → revocation → controller path is exercised end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@org.springframework.context.annotation.Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class CookieAuthenticationIntegrationTest {

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
    void cookieOnlyRequestAuthenticatesAndGetUserReturnsAccount() {
        IssuedAccount issued = issueRealTokenForNewAccount("Cookie Cat");

        webTestClient
            .get()
            .uri("/user")
            .header(HttpHeaders.COOKIE, cookieName + "=" + issued.token())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(issued.accountId())
            .jsonPath("$.displayName")
            .isEqualTo("Cookie Cat");
    }

    @Test
    void bearerHeaderWithSameTokenAlsoAuthenticates() {
        IssuedAccount issued = issueRealTokenForNewAccount("Bearer Bear");

        webTestClient
            .get()
            .uri("/user")
            .headers(headers -> headers.setBearerAuth(issued.token()))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(issued.accountId());
    }

    @Test
    void noCredentialsIsUnauthorized() {
        webTestClient.get().uri("/user").exchange().expectStatus().isUnauthorized();
    }

    // NOTE on CSRF: the cookie-style POST-without-token → 403 contract is covered by
    // CsrfProtectionIntegrationTest (it asserts the 403→401 transition on /auth/logout). It is not
    // re-asserted here because, once the cookie authenticates, a logout/refresh proceeds to an audit
    // write whose partitioned auth_event schema is not materialized under the test profile's
    // ddl-auto:create (Liquibase is disabled for tests) — that would mask CSRF behind a 500. Keeping
    // this slice focused on resolution avoids that test-environment entanglement.

    private record IssuedAccount(String token, long accountId) {}

    private IssuedAccount issueRealTokenForNewAccount(String displayName) {
        Account account = accountRepository.save(new Account(displayName));
        HephaestusJwtIssuer.Token token = jwtIssuer.issue(principalFactory.forAccount(account), null, null);
        return new IssuedAccount(token.value(), account.getId());
    }
}

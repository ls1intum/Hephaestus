package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.TokenConstraints;
import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Issue #1323 acceptance: impersonation cannot outlive its {@code imp_exp} across a refresh — over the
 * LIVE chain (real ES256 issuer + decoder, no mock). One test per branch of the impersonation refresh:
 * in-box rotation, time-box auto-exit, and mid-session-promotion auto-exit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class ImpersonationLifecycleIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private HephaestusJwtIssuer jwtIssuer;

    @Autowired
    private JwtPrincipalFactory principalFactory;

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @Autowired
    private JwtSigningKeyService signingKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @BeforeEach
    void cleanSlate() {
        databaseTestUtils.cleanDatabase();
        signingKeyService.ensureActiveKey();
    }

    @Test
    void refreshWithinTheTimeBoxKeepsImpersonationCappedAtTheSameCeiling() {
        Account operator = persist("Operator", Account.AppRole.APP_ADMIN);
        Account target = persist("Target", Account.AppRole.USER);
        Instant loginTime = Instant.now();
        Instant sessionCeiling = loginTime.plus(Duration.ofHours(12)).truncatedTo(ChronoUnit.SECONDS);
        String operatorToken = jwtIssuer
            .issue(principalFactory.forAccount(operator), TokenConstraints.session(sessionCeiling, loginTime), null)
            .value();

        // Begin impersonation over the API — the operator's auth_time is fresh, so the step-up gate
        // passes; the default 1h time-box applies.
        String impersonationToken = beginImpersonation(operatorToken, target.getId());
        JsonNode claims = claimsOf(impersonationToken);
        assertThat(claims.get("sub").asString()).isEqualTo(String.valueOf(target.getId()));
        assertThat(claims.get("act").get("sub").asString()).isEqualTo(String.valueOf(operator.getId()));
        long impExp = claims.get("imp_exp").asLong();
        // The operator's session ceiling + auth_time are carried INTO the impersonation sub-session.
        assertThat(claims.get("session_exp").asLong()).isEqualTo(sessionCeiling.getEpochSecond());
        assertThat(claims.get("auth_time").asLong()).isEqualTo(loginTime.getEpochSecond());

        String rotated = refresh(impersonationToken);
        JsonNode rotatedClaims = claimsOf(rotated);
        assertThat(rotatedClaims.get("sub").asString()).isEqualTo(String.valueOf(target.getId()));
        assertThat(rotatedClaims.get("act").get("sub").asString()).isEqualTo(String.valueOf(operator.getId()));
        assertThat(rotatedClaims.get("imp_exp").asLong()).as("imp_exp is an ABSOLUTE ceiling").isEqualTo(impExp);
        assertThat(rotatedClaims.get("session_exp").asLong()).isEqualTo(sessionCeiling.getEpochSecond());
        assertThat(rotatedClaims.get("auth_time").asLong()).isEqualTo(loginTime.getEpochSecond());
        // Every impersonation token's exp is capped at the ceiling — no token can outlive imp_exp.
        assertThat(rotatedClaims.get("exp").asLong()).isLessThanOrEqualTo(impExp);
    }

    @Test
    void refreshNearTheCeilingAutoExitsToTheOperatorAndIsAudited() {
        Account operator = persist("Operator", Account.AppRole.APP_ADMIN);
        Account target = persist("Target", Account.AppRole.USER);
        Instant loginTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant sessionCeiling = loginTime.plus(Duration.ofHours(12));
        // An impersonation whose remaining lifetime (45s) is inside the 60s exit-skew window: the
        // token itself is still perfectly valid, but the NEXT rotation would mint a token doomed to
        // die before the client's next proactive renewal — refresh must auto-exit instead. (This is
        // exactly where the SPA keep-alive's last before-expiry refresh lands in production.)
        Instant nearCeiling = Instant.now().plus(Duration.ofSeconds(45)).truncatedTo(ChronoUnit.SECONDS);
        String impersonationToken = jwtIssuer
            .issue(
                principalFactory.forAccount(target),
                TokenConstraints.impersonation(operator.getId(), nearCeiling, sessionCeiling, loginTime),
                null
            )
            .value();

        String exited = refresh(impersonationToken);
        JsonNode exitedClaims = claimsOf(exited);
        assertThat(exitedClaims.get("sub").asString()).isEqualTo(String.valueOf(operator.getId()));
        assertThat(exitedClaims.has("act")).as("auto-exit must drop the act claim").isFalse();
        assertThat(exitedClaims.has("imp_exp")).isFalse();
        // The operator session is restored under its ORIGINAL absolute ceiling + auth_time — the
        // round-trip through impersonation must not mint a fresh unlimited operator session.
        assertThat(exitedClaims.get("session_exp").asLong()).isEqualTo(sessionCeiling.getEpochSecond());
        assertThat(exitedClaims.get("auth_time").asLong()).isEqualTo(loginTime.getEpochSecond());

        // The auto-exit is audited as IMPERSONATION_END (EXPIRED) attributed to both parties.
        var ends = authEventRepository
            .findByAccountSince(target.getId(), Instant.now().minus(1, ChronoUnit.HOURS))
            .stream()
            .filter(e -> e.getEventType() == AuthEvent.EventType.IMPERSONATION_END)
            .toList();
        assertThat(ends).hasSize(1);
        assertThat(ends.get(0).getActingAccountId()).isEqualTo(operator.getId());
        assertThat(ends.get(0).getDetails()).contains("EXPIRED");
    }

    @Test
    void refreshAutoExitsIfTheTargetWasPromotedToAdminMidSession() {
        // begin() refuses admin→admin, but a refresh re-reads the target's roles from the DB. If the
        // target is promoted to APP_ADMIN mid-impersonation, the next refresh must auto-exit rather than
        // hand the operator an act-token carrying app_admin. Real chain: the promotion is a DB write.
        Account operator = persist("Operator", Account.AppRole.APP_ADMIN);
        Account target = persist("Target", Account.AppRole.USER);
        Instant loginTime = Instant.now();
        String operatorToken = jwtIssuer
            .issue(
                principalFactory.forAccount(operator),
                TokenConstraints.session(loginTime.plus(Duration.ofHours(12)), loginTime),
                null
            )
            .value();
        String impersonationToken = beginImpersonation(operatorToken, target.getId());

        target.setAppRole(Account.AppRole.APP_ADMIN);
        accountRepository.save(target);

        JsonNode exited = claimsOf(refresh(impersonationToken));
        assertThat(exited.get("sub").asString()).isEqualTo(String.valueOf(operator.getId()));
        assertThat(exited.has("act")).as("a mid-session promotion must not be carried into a rotation").isFalse();

        var ends = authEventRepository
            .findByAccountSince(target.getId(), Instant.now().minus(1, ChronoUnit.HOURS))
            .stream()
            .filter(e -> e.getEventType() == AuthEvent.EventType.IMPERSONATION_END)
            .toList();
        assertThat(ends).hasSize(1);
        assertThat(ends.get(0).getDetails()).contains("TARGET_PROMOTED");
    }

    private Account persist(String name, Account.AppRole role) {
        Account account = new Account(name);
        account.setAppRole(role);
        account.setStatus(Account.Status.ACTIVE);
        return accountRepository.save(account);
    }

    /** POST /auth/impersonate as the operator; returns the impersonation cookie-JWT. */
    private String beginImpersonation(String operatorToken, Long targetAccountId) {
        var result = webTestClient
            .post()
            .uri("/auth/impersonate")
            .headers(h -> h.setBearerAuth(operatorToken))
            .bodyValue(Map.of("targetAccountId", targetAccountId, "reason", "integration-test"))
            .exchange()
            .expectStatus()
            .isNoContent()
            .returnResult(Void.class);
        ResponseCookie cookie = result.getResponseCookies().getFirst(AuthProperties.DEFAULT_COOKIE_NAME);
        assertThat(cookie).as("impersonate must Set-Cookie the impersonation token").isNotNull();
        return cookie.getValue();
    }

    /** POST /auth/refresh presenting {@code token} as bearer (CSRF only gates the cookie path). */
    private String refresh(String token) {
        var result = webTestClient
            .post()
            .uri("/auth/refresh")
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isNoContent()
            .returnResult(Void.class);
        ResponseCookie rotated = result.getResponseCookies().getFirst(AuthProperties.DEFAULT_COOKIE_NAME);
        assertThat(rotated).as("refresh must Set-Cookie a rotated token").isNotNull();
        return rotated.getValue();
    }

    /** Decode the JWT payload (claims) without signature verification — assertions only. */
    private JsonNode claimsOf(String jwt) {
        String payload = jwt.split("\\.")[1];
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}

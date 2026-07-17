package de.tum.cit.aet.hephaestus.core.auth.stepup;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.TokenConstraints;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProviderService;
import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
 * Issue #1323 acceptance: the step-up gate on high-risk admin actions, over the LIVE chain (real
 * ES256 cookie-JWT + decoder). The happy path is covered by {@code AccountAdminRoleIntegrationTest}
 * and {@code ImpersonationLifecycleIntegrationTest} (both mint login-shaped tokens); this suite pins
 * the DENIAL contract: 403 + {@code code=step_up_required}, no side effects, an audited FAILURE row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class StepUpGateIntegrationTest {

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

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private LoginProviderService loginProviderService;

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
    void roleChangeWithStaleAuthTimeIsChallengedAndAudited() {
        Account admin = persist("Stale Admin", Account.AppRole.APP_ADMIN);
        Account user = persist("Target User", Account.AppRole.USER);

        webTestClient
            .patch()
            .uri("/admin/users/{id}", user.getId())
            .headers(h -> h.setBearerAuth(staleAuthToken(admin)))
            .bodyValue(Map.of("appRole", "APP_ADMIN"))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("step_up_required")
            .jsonPath("$.maxAgeSeconds")
            .isEqualTo(authProperties.stepUpMaxAge().toSeconds());

        // No side effect: the role must be unchanged.
        assertThat(accountRepository.findById(user.getId()))
            .get()
            .extracting(Account::getAppRole)
            .isEqualTo(Account.AppRole.USER);
        // The denial is audited on the gated action's own event type.
        assertFailureAudited(user.getId(), admin.getId(), AuthEvent.EventType.APP_ROLE_CHANGED);
    }

    @Test
    void impersonateBeginWithStaleAuthTimeIsChallengedAndAudited() {
        Account admin = persist("Stale Operator", Account.AppRole.APP_ADMIN);
        Account target = persist("Impersonation Target", Account.AppRole.USER);

        var result = webTestClient
            .post()
            .uri("/auth/impersonate")
            .headers(h -> h.setBearerAuth(staleAuthToken(admin)))
            .bodyValue(Map.of("targetAccountId", target.getId(), "reason", "should be blocked"))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("step_up_required")
            .returnResult();

        // No impersonation cookie escapes on the denial path.
        assertThat(result.getResponseCookies().getFirst(AuthProperties.DEFAULT_COOKIE_NAME)).isNull();
        assertFailureAudited(target.getId(), admin.getId(), AuthEvent.EventType.IMPERSONATION_BEGIN);
    }

    @Test
    void loginProviderMutationWithStaleAuthTimeIsChallenged() {
        // Closes a bypass of the gate itself: repointing a provider's baseUrl swings the IdP this
        // instance trusts for the identities behind it, which is an admin→admin takeover primitive —
        // and it would hand the attacker a token with a FRESH auth_time, defeating the other gates.
        Account admin = persist("Stale Admin", Account.AppRole.APP_ADMIN);
        loginProviderService.create(
            new LoginProviderService.Draft(
                "gitlab-step-up",
                LoginProvider.ProviderType.GITLAB,
                "GitLab",
                "https://gitlab.step-up.test",
                "client-id",
                "client-secret",
                null
            ),
            admin.getId(),
            Instant.now()
        );

        webTestClient
            .patch()
            .uri("/admin/login-providers/{id}", "gitlab-step-up")
            .headers(h -> h.setBearerAuth(staleAuthToken(admin)))
            .bodyValue(Map.of("baseUrl", "https://evil.example"))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("step_up_required");

        // The provider is untouched: the IdP this instance trusts did not move.
        assertThat(loginProviderService.require("gitlab-step-up").getBaseUrl()).isEqualTo(
            "https://gitlab.step-up.test"
        );
    }

    @Test
    void tokenWithoutAuthTimeIsTreatedAsStale() {
        // Fail-safe: a token minted WITHOUT the claim (pre-#1323 mint) must not pass the gate.
        Account admin = persist("Legacy Admin", Account.AppRole.APP_ADMIN);
        Account user = persist("Another User", Account.AppRole.USER);
        String legacyToken = jwtIssuer.issue(principalFactory.forAccount(admin), TokenConstraints.none(), null).value();

        webTestClient
            .patch()
            .uri("/admin/users/{id}", user.getId())
            .headers(h -> h.setBearerAuth(legacyToken))
            .bodyValue(Map.of("appRole", "APP_ADMIN"))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("step_up_required");
    }

    @Test
    void staleTokenOnANonexistentProviderIs404NotChallenged() {
        // Gate ordering: it runs AFTER resource-existence, so a stale token targeting a provider that
        // does not exist gets the ordinary 404 — not step_up_required, and no audited FAILURE row.
        Account admin = persist("Stale Admin", Account.AppRole.APP_ADMIN);

        webTestClient
            .patch()
            .uri("/admin/login-providers/{id}", "no-such-provider")
            .headers(h -> h.setBearerAuth(staleAuthToken(admin)))
            .bodyValue(Map.of("baseUrl", "https://gitlab.example.com"))
            .exchange()
            .expectStatus()
            .isNotFound();

        assertThat(
            authEventRepository
                .findAll()
                .stream()
                .noneMatch(e -> e.getEventType() == AuthEvent.EventType.LOGIN_PROVIDER_CHANGED)
        ).isTrue();
    }

    @Test
    void freshAuthAdminCanCreateThenDeleteAProviderAndBothAreAudited() {
        // The happy path: proves the gate does not block a fresh-auth admin, AND that CREATE + DELETE
        // each write their LOGIN_PROVIDER_CHANGED row — so dropping the audit from either path fails here.
        Account admin = persist("Fresh Admin", Account.AppRole.APP_ADMIN);
        String token = freshAuthToken(admin);
        // A second enabled provider so deleting the one under test isn't refused as the last sign-in path.
        createProvider(token, "gitlab-keep", "https://gitlab.keep.test");

        createProvider(token, "gitlab-fresh", "https://gitlab.fresh.test");

        webTestClient
            .delete()
            .uri("/admin/login-providers/{id}", "gitlab-fresh")
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isNoContent();

        var actions = authEventRepository
            .findAll()
            .stream()
            .filter(e -> e.getEventType() == AuthEvent.EventType.LOGIN_PROVIDER_CHANGED)
            .map(e -> e.getDetails())
            .toList();
        assertThat(actions)
            .anyMatch(d -> d.contains("gitlab-fresh") && d.contains("CREATE"))
            .anyMatch(d -> d.contains("gitlab-fresh") && d.contains("DELETE"));
    }

    private void createProvider(String bearer, String registrationId, String baseUrl) {
        webTestClient
            .post()
            .uri("/admin/login-providers")
            .headers(h -> h.setBearerAuth(bearer))
            .bodyValue(
                Map.of(
                    "registrationId",
                    registrationId,
                    "type",
                    "GITLAB",
                    "baseUrl",
                    baseUrl,
                    "clientId",
                    "cid",
                    "clientSecret",
                    "secret"
                )
            )
            .exchange()
            .expectStatus()
            .isCreated();
    }

    private Account persist(String name, Account.AppRole role) {
        Account account = new Account(name);
        account.setAppRole(role);
        account.setStatus(Account.Status.ACTIVE);
        return accountRepository.save(account);
    }

    /** A structurally valid session whose interactive login is OLDER than the step-up window. */
    private String staleAuthToken(Account account) {
        Instant staleLogin = Instant.now().minus(authProperties.stepUpMaxAge()).minus(Duration.ofMinutes(1));
        return sessionToken(account, staleLogin);
    }

    /** A session whose interactive login is fresh — passes the gate. */
    private String freshAuthToken(Account account) {
        return sessionToken(account, Instant.now());
    }

    private String sessionToken(Account account, Instant authTime) {
        return jwtIssuer
            .issue(
                principalFactory.forAccount(account),
                TokenConstraints.session(Instant.now().plus(Duration.ofHours(1)), authTime),
                null
            )
            .value();
    }

    private void assertFailureAudited(Long accountId, Long actingAccountId, AuthEvent.EventType type) {
        var denials = authEventRepository
            .findByAccountSince(accountId, Instant.now().minus(1, ChronoUnit.HOURS))
            .stream()
            .filter(e -> e.getEventType() == type && e.getResult() == AuthEvent.Result.FAILURE)
            .toList();
        assertThat(denials).hasSize(1);
        assertThat(denials.get(0).getActingAccountId()).isEqualTo(actingAccountId);
        assertThat(denials.get(0).getFailureReason()).isEqualTo("step_up_required");
    }
}

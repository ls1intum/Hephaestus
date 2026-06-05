package de.tum.cit.aet.hephaestus.core.auth.web;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventData;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.time.Instant;
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
 * {@code GET /admin/audit} — the read-only instance-admin audit viewer against the <b>real</b>
 * security chain (genuine cookie-JWT, no mock decoder), mirroring {@code AccountAdminRoleIntegrationTest}.
 *
 * <p>Asserts (a) the {@code app_admin} authority gate (a plain user is 403'd), (b) that seeded events
 * are surfaced newest-first with the attribution fields intact, and (c) that the {@code eventType}
 * filter narrows the result. Events are seeded directly through the repository so the test exercises
 * the VIEWER deterministically (the audit write path swallows failures by design). Requires Docker,
 * like every {@code @Tag("integration")}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class AuthAuditControllerIntegrationTest {

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
    void plainUserIsForbidden() {
        Account user = persist("Plain User", Account.AppRole.USER);

        webTestClient
            .get()
            .uri("/admin/audit")
            .headers(h -> h.setBearerAuth(tokenFor(user)))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void adminSeesEventsNewestFirstWithAttribution() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        Account target = persist("Target", Account.AppRole.USER);
        // Older login, then a newer role change (acted by the admin on the target).
        seed(1L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-01T10:00:00Z"), target.getId(), null);
        seed(
            2L,
            AuthEvent.EventType.APP_ROLE_CHANGED,
            Instant.parse("2026-06-02T10:00:00Z"),
            target.getId(),
            admin.getId()
        );

        webTestClient
            .get()
            .uri("/admin/audit")
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(2)
            // Newest first: the role change leads.
            .jsonPath("$.content[0].eventType")
            .isEqualTo("APP_ROLE_CHANGED")
            .jsonPath("$.content[0].accountId")
            .isEqualTo(target.getId())
            .jsonPath("$.content[0].actingAccountId")
            .isEqualTo(admin.getId())
            .jsonPath("$.content[1].eventType")
            .isEqualTo("LOGIN");
    }

    @Test
    void eventTypeFilterNarrowsResults() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        seed(1L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-01T10:00:00Z"), admin.getId(), null);
        seed(2L, AuthEvent.EventType.APP_ROLE_CHANGED, Instant.parse("2026-06-02T10:00:00Z"), admin.getId(), null);

        webTestClient
            .get()
            .uri(builder -> builder.path("/admin/audit").queryParam("eventType", "APP_ROLE_CHANGED").build())
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1)
            .jsonPath("$.content[0].eventType")
            .isEqualTo("APP_ROLE_CHANGED");
    }

    @Test
    void resolvesAccountAndActorToHumanIdentities() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        Account target = persist("Target User", Account.AppRole.USER);
        seed(
            1L,
            AuthEvent.EventType.APP_ROLE_CHANGED,
            Instant.parse("2026-06-02T10:00:00Z"),
            target.getId(),
            admin.getId()
        );

        webTestClient
            .get()
            .uri("/admin/audit")
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content[0].account.displayName")
            .isEqualTo("Target User")
            .jsonPath("$.content[0].actor.displayName")
            .isEqualTo("Keeper Admin");
    }

    @Test
    void resultFilterNarrowsToFailuresWithReason() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        seed(1L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-01T10:00:00Z"), admin.getId(), null);
        seedFailure(2L, AuthEvent.EventType.LOGIN_FAILED, Instant.parse("2026-06-02T10:00:00Z"), "Email not verified");

        webTestClient
            .get()
            .uri(builder -> builder.path("/admin/audit").queryParam("result", "FAILURE").build())
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1)
            .jsonPath("$.content[0].result")
            .isEqualTo("FAILURE")
            .jsonPath("$.content[0].failureReason")
            .isEqualTo("Email not verified");
    }

    @Test
    void timeRangeFilterNarrowsToWindow() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        seed(1L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-01T10:00:00Z"), admin.getId(), null);
        seed(2L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-05T10:00:00Z"), admin.getId(), null);

        webTestClient
            .get()
            .uri(builder ->
                builder
                    .path("/admin/audit")
                    .queryParam("from", "2026-06-04T00:00:00Z")
                    .queryParam("to", "2026-06-06T00:00:00Z")
                    .build()
            )
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1)
            .jsonPath("$.content[0].occurredAt")
            .value(v -> org.assertj.core.api.Assertions.assertThat((String) v).startsWith("2026-06-05"));
    }

    @Test
    void actorFilterReconstructsImpersonationSession() {
        Account admin = persist("Keeper Admin", Account.AppRole.APP_ADMIN);
        Account target = persist("Target", Account.AppRole.USER);
        // One self-login (no actor) + one acted-by-admin event.
        seed(1L, AuthEvent.EventType.LOGIN, Instant.parse("2026-06-01T10:00:00Z"), target.getId(), null);
        seed(
            2L,
            AuthEvent.EventType.IMPERSONATION_BEGIN,
            Instant.parse("2026-06-02T10:00:00Z"),
            target.getId(),
            admin.getId()
        );

        webTestClient
            .get()
            .uri(builder -> builder.path("/admin/audit").queryParam("actingAccountId", admin.getId()).build())
            .headers(h -> h.setBearerAuth(tokenFor(admin)))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1)
            .jsonPath("$.content[0].eventType")
            .isEqualTo("IMPERSONATION_BEGIN");
    }

    private void seedFailure(long id, AuthEvent.EventType type, Instant occurredAt, String failureReason) {
        AuthEventData data = new AuthEventData(
            type,
            AuthEvent.Result.FAILURE,
            null,
            null,
            failureReason,
            null,
            null,
            null,
            null
        );
        authEventRepository.save(AuthEvent.create(data, id, occurredAt, "127.0.0.1", "test-agent"));
    }

    private void seed(long id, AuthEvent.EventType type, Instant occurredAt, Long accountId, Long actingAccountId) {
        AuthEventData data = new AuthEventData(
            type,
            AuthEvent.Result.SUCCESS,
            accountId,
            actingAccountId,
            null,
            null,
            null,
            null,
            null
        );
        authEventRepository.save(AuthEvent.create(data, id, occurredAt, "127.0.0.1", "test-agent"));
    }

    private Account persist(String displayName, Account.AppRole role) {
        Account account = new Account(displayName);
        account.setAppRole(role);
        account.setStatus(Account.Status.ACTIVE);
        return accountRepository.save(account);
    }

    private String tokenFor(Account account) {
        return jwtIssuer.issue(principalFactory.forAccount(account), null, null).value();
    }
}

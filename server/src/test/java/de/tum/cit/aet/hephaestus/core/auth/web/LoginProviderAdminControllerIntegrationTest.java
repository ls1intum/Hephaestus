package de.tum.cit.aet.hephaestus.core.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
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

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    /**
     * A signed-in instance admin. Uses the numeric-subject mock token because these endpoints now
     * resolve the acting account (they are step-up gated + audited, #1323) — the legacy
     * {@code admin-user-id} subject is not an account id.
     */
    private Account persistAdmin() {
        Account admin = new Account("Provider Admin");
        admin.setAppRole(Account.AppRole.APP_ADMIN);
        admin.setStatus(Account.Status.ACTIVE);
        return accountRepository.save(admin);
    }

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
    void adminCanCreateAndListWithoutLeakingTheSecret() {
        Account admin = persistAdmin();
        String token = "mock-jwt-sub-" + admin.getId();
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
            .headers(h -> h.setBearerAuth(token))
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
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[?(@.registrationId == 'gitlab-actest')]")
            .exists();

        // Mutating the IdP this instance trusts is audited and attributed (#1323). The event has no
        // subject account — only an actor — so it is found by scan, not by findByAccountSince.
        var changes = authEventRepository
            .findAll()
            .stream()
            .filter(e -> e.getEventType() == AuthEvent.EventType.LOGIN_PROVIDER_CHANGED)
            .toList();
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getActingAccountId()).isEqualTo(admin.getId());
        assertThat(changes.get(0).getDetails()).contains("gitlab-actest").contains("CREATE");
    }
}

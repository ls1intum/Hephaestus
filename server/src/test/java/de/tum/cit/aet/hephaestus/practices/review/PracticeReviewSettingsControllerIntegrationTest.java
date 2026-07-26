package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * HTTP-boundary coverage for a workspace's practice-review policy: the read, and the PATCH
 * (override / reset-to-inherit, 400 on an out-of-range cooldown). Model selection lives on the
 * per-purpose agents ({@code /agents}) and is covered there.
 */
class PracticeReviewSettingsControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "AI Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    @Test
    @WithAdminUser
    void readReturnsInheritedPolicyAndNothingElse() {
        Workspace workspace = setupWorkspace("review-read");

        webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            // A fresh workspace overrides nothing, so every raw override is absent and the effective
            // value is the fleet default.
            .jsonPath("$.skipDraftsOverride")
            .doesNotExist()
            .jsonPath("$.skipDrafts")
            .isEqualTo(true)
            // The workspace's feature flags used to be copied onto this response. They are not the
            // review policy and every client already holds them on the workspace itself, so their
            // absence here is the point of the endpoint's rename — assert it rather than assume it.
            .jsonPath("$.practicesEnabled")
            .doesNotExist()
            .jsonPath("$.mentorEnabled")
            .doesNotExist()
            .jsonPath("$.workspaceConnectionsAllowed")
            .doesNotExist();
    }

    @Test
    @WithAdminUser
    void rejectsOutOfRangeCooldownWith400() {
        Workspace workspace = setupWorkspace("ai-cooldown");

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("cooldownMinutes", 5000))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void overridesAndResetsPracticeReviewPolicy() {
        Workspace workspace = setupWorkspace("ai-reset");

        // Override skipDrafts to false (fleet default is true).
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("skipDrafts", false))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skipDraftsOverride")
            .isEqualTo(false)
            .jsonPath("$.skipDrafts")
            .isEqualTo(false);

        // Reset it back to inherit → override null, effective falls back to the fleet default (true).
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("reset", List.of("SKIP_DRAFTS")))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skipDraftsOverride")
            .doesNotExist()
            .jsonPath("$.skipDrafts")
            .isEqualTo(true);
    }

    // ─── Access control ────────────────────────────────────────────────────────────────────────
    // Both handlers are @RequireAtLeastWorkspaceAdmin, and gating this policy IS the controller's
    // reason to exist — the read tells you what the fleet default is and the PATCH changes whether
    // reviews run at all. Three @WithAdminUser happy paths said nothing about that gate.

    @Test
    @WithMentorUser
    void aWorkspaceMemberCanNeitherReadNorChangeThePolicy() {
        Workspace workspace = setupWorkspace("review-member");
        // Login must match @WithMentorUser's default "mentor" principal so the membership resolver
        // finds this exact row.
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);
        String slug = workspace.getWorkspaceSlug();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", slug)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", slug)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("skipDrafts", false))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithMentorUser
    void aWorkspaceAdminWithNoMembershipInAnotherWorkspaceIsForbiddenThere() {
        // A genuine (non-superadmin) workspace ADMIN: @WithAdminUser carries the instance-wide
        // app_admin elevation, which would admit this call for the wrong reason.
        User admin = persistUser("mentor");
        Workspace own = createWorkspace("review-own", "Own", "review-own-org", AccountType.ORG, admin);
        ensureWorkspaceMembership(own, admin, WorkspaceRole.ADMIN);

        Workspace other = createWorkspace(
            "review-other",
            "Other",
            "review-other-org",
            AccountType.ORG,
            persistUser("review-other-owner")
        );

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", other.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("skipDrafts", false))
            .exchange()
            .expectStatus()
            .isForbidden();

        // Sanity: the same principal IS admitted in the workspace it actually administers.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", own.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void anonymousIsUnauthorized() {
        // Against a REAL workspace: an unknown slug 404s during resolution, which would pass this
        // assertion without ever reaching the authentication check.
        User owner = persistUser("review-anon-owner");
        Workspace workspace = createWorkspace("review-anon", "Anon", "review-anon-org", AccountType.ORG, owner);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();

        // The PATCH is 403 where the GET above is 401: with no `Authorization: Bearer` header a
        // state-changing request is cookie-shaped, so SecurityConfig#requiresCsrf refuses it at the
        // CSRF filter before authentication runs. Asserting 401 would invite exempting the mutation
        // from CSRF to "fix" it. Either way the handler is never reached.
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", workspace.getWorkspaceSlug())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("skipDrafts", false))
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}

package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
}

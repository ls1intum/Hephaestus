package de.tum.cit.aet.hephaestus.agent.settings;

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
 * HTTP-boundary coverage for the AI-settings surface: the aggregate read, and the practice-review
 * PATCH (override / reset-to-inherit, 400 on an out-of-range cooldown). Model selection itself moved
 * to the per-purpose agent bindings ({@code /agent-bindings}) and is covered there.
 */
class AiSettingsControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

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
    void aggregateReadReturnsFeatureFlagsAndInheritedPolicy() {
        Workspace workspace = setupWorkspace("ai-read");

        webTestClient
            .get()
            .uri("/workspaces/{slug}/ai-settings", workspace.getWorkspaceSlug())
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
            .jsonPath("$.practicesEnabled")
            .isEqualTo(false)
            .jsonPath("$.mentorEnabled")
            .isEqualTo(false);
    }

    @Test
    @WithAdminUser
    void rejectsOutOfRangeCooldownWith400() {
        Workspace workspace = setupWorkspace("ai-cooldown");

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
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
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
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
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
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

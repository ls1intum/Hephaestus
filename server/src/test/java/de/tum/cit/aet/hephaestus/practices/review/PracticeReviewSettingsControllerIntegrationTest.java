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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

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
            .jsonPath("$.deliverToMergedOverride")
            .doesNotExist()
            .jsonPath("$.deliverToMerged")
            .isEqualTo(false)
            // Whether a draft occasions a review is a property of the practice's binding, so the policy
            // carries no fleet-wide veto — absent, not merely defaulted.
            .jsonPath("$.skipDrafts")
            .doesNotExist()
            .jsonPath("$.skipDraftsOverride")
            .doesNotExist()
            // Feature flags aren't review policy and already live on the workspace itself.
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
        String slug = workspace.getWorkspaceSlug();

        patch(slug, currentEtag(slug), Map.of("deliverToMerged", true))
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deliverToMergedOverride")
            .isEqualTo(true)
            .jsonPath("$.deliverToMerged")
            .isEqualTo(true);

        // Reset to inherit — the fleet default for deliverToMerged is false.
        patch(slug, currentEtag(slug), Map.of("reset", List.of("DELIVER_TO_MERGED")))
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deliverToMergedOverride")
            .doesNotExist()
            .jsonPath("$.deliverToMerged")
            .isEqualTo(false);
    }

    @Test
    @WithAdminUser
    void refusesAPolicyChangeThatNamesNoVersion() {
        Workspace workspace = setupWorkspace("ai-unconditional");

        patch(workspace.getWorkspaceSlug(), null, Map.of("deliverToMerged", true))
            .expectStatus()
            .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    }

    private String currentEtag(String slug) {
        return webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", slug)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(PracticeReviewSettingsDTO.class)
            .getResponseHeaders()
            .getETag();
    }

    private WebTestClient.ResponseSpec patch(String slug, @Nullable String ifMatch, Map<String, Object> body) {
        return webTestClient
            .patch()
            .uri("/workspaces/{slug}/practices/review-settings", slug)
            .headers(headers -> {
                TestAuthUtils.withCurrentUser().accept(headers);
                if (ifMatch != null) headers.setIfMatch(ifMatch);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange();
    }

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

        webTestClient
            .get()
            .uri("/workspaces/{slug}/practices/review-settings", own.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();
    }
}

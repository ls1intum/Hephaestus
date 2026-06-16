package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeAreasRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Access-control coverage for {@link PracticeAreaController}.
 *
 * <p>Read operations are annotated {@code @SecurityRequirements} (any workspace member); the four
 * mutating operations (create / update / reorder / delete) are {@code @RequireAtLeastWorkspaceAdmin}.
 * These tests assert that a plain workspace MEMBER is forbidden on every mutation and permitted on
 * reads, and that anonymous callers are rejected. Functional CRUD behaviour for the bind endpoint
 * lives on {@code PracticeCatalogControllerIntegrationTest}.
 */
class PracticeAreaControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practice-goals";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeAreaRepository goalRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("goal-owner");
        workspace = createWorkspace("goal-ws", "Goal WS", "goal-org", AccountType.ORG, owner);
    }

    private PracticeArea persistGoal(String slug, String name) {
        PracticeArea goal = new PracticeArea();
        goal.setWorkspace(workspace);
        goal.setSlug(slug);
        goal.setName(name);
        return goalRepository.save(goal);
    }

    private CreatePracticeAreaRequestDTO validCreateRequest(String slug) {
        return new CreatePracticeAreaRequestDTO(slug, "Goal " + slug, "Develops " + slug, null);
    }

    /** Registers the current {@code @WithMentorUser} principal as a plain workspace MEMBER. */
    private void asMember() {
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    // LIST — read, member-allowed (@SecurityRequirements)

    @Nested
    @DisplayName("GET /practice-goals")
    class ListGoals {

        @Test
        @WithAdminUser
        void shouldReturnGoalsForAdmin() {
            ensureAdminMembership(workspace);
            persistGoal("alpha", "Alpha");
            persistGoal("beta", "Beta");

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2);
        }

        @Test
        @WithMentorUser
        @DisplayName("allows a plain workspace member to list")
        void shouldAllowMemberToList() {
            asMember();
            persistGoal("member-visible", "Visible");

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient.get().uri(BASE_URI, workspace.getWorkspaceSlug()).exchange().expectStatus().isUnauthorized();
        }
    }

    // GET SINGLE — read, member-allowed (@SecurityRequirements)

    @Nested
    @DisplayName("GET /practice-goals/{goalSlug}")
    class GetGoal {

        @Test
        @WithMentorUser
        @DisplayName("allows a plain workspace member to get a goal")
        void shouldAllowMemberToGet() {
            asMember();
            persistGoal("member-get", "Member Get");

            webTestClient
                .get()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "member-get")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.slug")
                .isEqualTo("member-get");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .get()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // CREATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("POST /practice-goals")
    class CreateGoal {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from creating a goal")
        void shouldReturn403ForNonAdmin() {
            asMember();

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("forbidden-goal"))
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(goalRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-goal")).isFalse();
        }

        @Test
        @DisplayName("rejects anonymous create (403 via CSRF gate, before auth)")
        void shouldRejectAnonymousCreate() {
            // Anonymous POST → double-submit CSRF gate (ADR 0017) rejects 403 before auth (no
            // X-XSRF-TOKEN). The create stays blocked for anonymous callers.
            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("anon-goal"))
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    // UPDATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("PATCH /practice-goals/{goalSlug}")
    class UpdateGoal {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from updating a goal")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistGoal("forbidden-update", "Original");

            var request = new UpdatePracticeAreaRequestDTO("Hacked Name", null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "forbidden-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();

            PracticeArea persisted = goalRepository
                .findByWorkspaceIdAndSlug(workspace.getId(), "forbidden-update")
                .orElseThrow();
            assertThat(persisted.getName()).isEqualTo("Original");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            var request = new UpdatePracticeAreaRequestDTO("Name", null, null, null);

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // REORDER — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("PATCH /practice-goals/reorder")
    class ReorderGoals {

        @Test
        @WithAdminUser
        @DisplayName("reorders goals for an admin and persists the new display order")
        void shouldReorderForAdmin() {
            ensureAdminMembership(workspace);
            persistGoal("first", "First");
            persistGoal("second", "Second");

            var request = new ReorderPracticeAreasRequestDTO(List.of("second", "first"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].slug")
                .isEqualTo("second")
                .jsonPath("$[1].slug")
                .isEqualTo("first");

            assertThat(
                goalRepository.findByWorkspaceIdAndSlug(workspace.getId(), "second").orElseThrow().getDisplayOrder()
            ).isZero();
            assertThat(
                goalRepository.findByWorkspaceIdAndSlug(workspace.getId(), "first").orElseThrow().getDisplayOrder()
            ).isEqualTo(1);
        }

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from reordering goals")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistGoal("first", "First");
            persistGoal("second", "Second");

            var request = new ReorderPracticeAreasRequestDTO(List.of("second", "first"));

            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            var request = new ReorderPracticeAreasRequestDTO(List.of("first", "second"));

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/reorder", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // DELETE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("DELETE /practice-goals/{goalSlug}")
    class DeleteGoal {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from deleting a goal")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistGoal("forbidden-delete", "Keep Me");

            webTestClient
                .delete()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "forbidden-delete")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(goalRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-delete")).isTrue();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .delete()
                .uri(BASE_URI + "/{goalSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }
}

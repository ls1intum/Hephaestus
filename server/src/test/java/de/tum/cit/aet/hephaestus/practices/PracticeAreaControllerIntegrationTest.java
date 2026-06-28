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

    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practice-areas";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeAreaRepository areaRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("area-owner");
        workspace = createWorkspace("area-ws", "Area WS", "area-org", AccountType.ORG, owner);
    }

    private PracticeArea persistArea(String slug, String name) {
        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName(name);
        return areaRepository.save(area);
    }

    private CreatePracticeAreaRequestDTO validCreateRequest(String slug) {
        return new CreatePracticeAreaRequestDTO(slug, "Area " + slug, "Develops " + slug, null, "Package", "sky");
    }

    /** Registers the current {@code @WithMentorUser} principal as a plain workspace MEMBER. */
    private void asMember() {
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    // LIST — read, member-allowed (@SecurityRequirements)

    @Nested
    @DisplayName("GET /practice-areas")
    class ListAreas {

        @Test
        @WithAdminUser
        void shouldReturnAreasForAdmin() {
            ensureAdminMembership(workspace);
            persistArea("alpha", "Alpha");
            persistArea("beta", "Beta");

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
            persistArea("member-visible", "Visible");

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
    @DisplayName("GET /practice-areas/{areaSlug}")
    class GetArea {

        @Test
        @WithMentorUser
        @DisplayName("allows a plain workspace member to get an area")
        void shouldAllowMemberToGet() {
            asMember();
            persistArea("member-get", "Member Get");

            webTestClient
                .get()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "member-get")
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
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // CREATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("POST /practice-areas")
    class CreateArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from creating an area")
        void shouldReturn403ForNonAdmin() {
            asMember();

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("forbidden-area"))
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(areaRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-area")).isFalse();
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
                .bodyValue(validCreateRequest("anon-area"))
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    // UPDATE — @RequireAtLeastWorkspaceAdmin

    @Nested
    @DisplayName("PATCH /practice-areas/{areaSlug}")
    class UpdateArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from updating an area")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("forbidden-update", "Original");

            var request = new UpdatePracticeAreaRequestDTO("Hacked Name", null, null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "forbidden-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();

            PracticeArea persisted = areaRepository
                .findByWorkspaceIdAndSlug(workspace.getId(), "forbidden-update")
                .orElseThrow();
            assertThat(persisted.getName()).isEqualTo("Original");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            var request = new UpdatePracticeAreaRequestDTO("Name", null, null, null, null, null);

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
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
    @DisplayName("PATCH /practice-areas/reorder")
    class ReorderAreas {

        @Test
        @WithAdminUser
        @DisplayName("reorders areas for an admin and persists the new display order")
        void shouldReorderForAdmin() {
            ensureAdminMembership(workspace);
            persistArea("first", "First");
            persistArea("second", "Second");

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
                areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), "second").orElseThrow().getDisplayOrder()
            ).isZero();
            assertThat(
                areaRepository.findByWorkspaceIdAndSlug(workspace.getId(), "first").orElseThrow().getDisplayOrder()
            ).isEqualTo(1);
        }

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from reordering areas")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("first", "First");
            persistArea("second", "Second");

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
    @DisplayName("DELETE /practice-areas/{areaSlug}")
    class DeleteArea {

        @Test
        @WithMentorUser
        @DisplayName("forbids a plain workspace member from deleting an area")
        void shouldReturn403ForNonAdmin() {
            asMember();
            persistArea("forbidden-delete", "Keep Me");

            webTestClient
                .delete()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "forbidden-delete")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();

            assertThat(areaRepository.existsByWorkspaceIdAndSlug(workspace.getId(), "forbidden-delete")).isTrue();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .delete()
                .uri(BASE_URI + "/{areaSlug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }
}

package de.tum.in.www1.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.dto.PracticeDTO;
import de.tum.in.www1.hephaestus.practices.dto.UpdatePracticeActiveRequestDTO;
import de.tum.in.www1.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Practice catalog controller integration")
class PracticeCatalogControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practices";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("catalog-owner");
        workspace = createWorkspace("catalog-ws", "Catalog WS", "catalog-org", AccountType.ORG, owner);
    }

    private Practice persistPractice(String slug, String name, String category, boolean active) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCategory(category);
        practice.setDescription("Description for " + slug);
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setDetectionPrompt("Detect prompt for " + slug);
        practice.setActive(active);
        return practiceRepository.save(practice);
    }

    private CreatePracticeRequestDTO validCreateRequest(String slug) {
        return new CreatePracticeRequestDTO(
            slug,
            "Practice " + slug,
            "test-category",
            "Description for " + slug,
            List.of("PullRequestCreated", "ReviewSubmitted"),
            "Detect if the PR follows best practices"
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIST
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices")
    class ListPractices {

        @Test
        @WithAdminUser
        @DisplayName("returns empty list when no practices exist")
        void shouldReturnEmptyList() {
            ensureAdminMembership(workspace);

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        @DisplayName("returns all practices for workspace")
        void shouldReturnAllPractices() {
            ensureAdminMembership(workspace);
            persistPractice("alpha", "Alpha", "cat-a", true);
            persistPractice("beta", "Beta", "cat-b", false);

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
        @WithAdminUser
        @DisplayName("returns practices ordered by name ascending")
        void shouldReturnOrderedByName() {
            ensureAdminMembership(workspace);
            persistPractice("z-slug", "Zebra", "cat", true);
            persistPractice("a-slug", "Alpha", "cat", true);
            persistPractice("m-slug", "Middle", "cat", true);

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(3)
                .jsonPath("$[0].name")
                .isEqualTo("Alpha")
                .jsonPath("$[1].name")
                .isEqualTo("Middle")
                .jsonPath("$[2].name")
                .isEqualTo("Zebra");
        }

        @Test
        @WithAdminUser
        @DisplayName("filters by category")
        void shouldFilterByCategory() {
            ensureAdminMembership(workspace);
            persistPractice("alpha", "Alpha", "cat-a", true);
            persistPractice("beta", "Beta", "cat-b", true);

            webTestClient
                .get()
                .uri(BASE_URI + "?category=cat-a", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].slug")
                .isEqualTo("alpha");
        }

        @Test
        @WithAdminUser
        @DisplayName("filters by active state")
        void shouldFilterByActive() {
            ensureAdminMembership(workspace);
            persistPractice("active-one", "Active", "cat", true);
            persistPractice("inactive-one", "Inactive", "cat", false);

            webTestClient
                .get()
                .uri(BASE_URI + "?active=true", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].slug")
                .isEqualTo("active-one");
        }

        @Test
        @WithAdminUser
        @DisplayName("filters by both category and active")
        void shouldFilterByCategoryAndActive() {
            ensureAdminMembership(workspace);
            persistPractice("a-active", "A Active", "cat-a", true);
            persistPractice("a-inactive", "A Inactive", "cat-a", false);
            persistPractice("b-active", "B Active", "cat-b", true);

            webTestClient
                .get()
                .uri(BASE_URI + "?category=cat-a&active=true", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].slug")
                .isEqualTo("a-active");
        }

        @Test
        @WithMentorUser
        @DisplayName("member (non-admin) can list practices")
        void shouldAllowMemberToList() {
            User member = persistUser("mentor");
            ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("member-visible", "Visible", "cat", true);

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

    // ══════════════════════════════════════════════════════════════════════════
    // GET SINGLE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices/{practiceSlug}")
    class GetPractice {

        @Test
        @WithAdminUser
        @DisplayName("returns practice by slug with all fields")
        void shouldReturnPractice() {
            ensureAdminMembership(workspace);
            persistPractice("target-practice", "Target Practice", "cat", true);

            PracticeDTO result = webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "target-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("target-practice");
            assertThat(result.name()).isEqualTo("Target Practice");
            assertThat(result.category()).isEqualTo("cat");
            assertThat(result.active()).isTrue();
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated");
            assertThat(result.detectionPrompt()).isEqualTo("Detect prompt for target-practice");
            assertThat(result.createdAt()).isNotNull();
            assertThat(result.updatedAt()).isNotNull();
        }

        @Test
        @WithMentorUser
        @DisplayName("member (non-admin) can get practice by slug")
        void shouldAllowMemberToGet() {
            User member = persistUser("mentor");
            ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("member-get", "Member Get", "cat", true);

            webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "member-get")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.slug")
                .isEqualTo("member-get");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent slug")
        void shouldReturn404() {
            ensureAdminMembership(workspace);

            ProblemDetail problem = webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "non-existent")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "any-slug")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /practices")
    class CreatePractice {

        @Test
        @WithAdminUser
        @DisplayName("creates practice and returns 201 with location header and all fields")
        void shouldCreatePractice() {
            ensureAdminMembership(workspace);

            PracticeDTO result = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("new-practice"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("new-practice");
            assertThat(result.name()).isEqualTo("Practice new-practice");
            assertThat(result.category()).isEqualTo("test-category");
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated", "ReviewSubmitted");
            assertThat(result.detectionPrompt()).isEqualTo("Detect if the PR follows best practices");
            assertThat(result.active()).isTrue();
            assertThat(result.id()).isNotNull();
            assertThat(result.createdAt()).isNotNull();
            assertThat(result.updatedAt()).isNotNull();

            // Verify database state matches response
            Optional<Practice> persisted = practiceRepository.findByWorkspaceIdAndSlug(
                workspace.getId(),
                "new-practice"
            );
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getName()).isEqualTo("Practice new-practice");
            assertThat(persisted.get().getCategory()).isEqualTo("test-category");
            assertThat(persisted.get().isActive()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("creates practice with only required fields (null optionals)")
        void shouldCreatePracticeWithMinimalFields() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "minimal-practice",
                "Minimal Practice",
                null,
                "A description",
                List.of("PullRequestCreated"),
                null
            );

            PracticeDTO result = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("minimal-practice");
            assertThat(result.category()).isNull();
            assertThat(result.detectionPrompt()).isNull();
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("accepts minimum length slug (3 chars)")
        void shouldAcceptMinLengthSlug() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("abc"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.slug")
                .isEqualTo("abc");
        }

        @Test
        @WithAdminUser
        @DisplayName("accepts maximum length slug (64 chars)")
        void shouldAcceptMaxLengthSlug() {
            ensureAdminMembership(workspace);
            String slug64 = "a".repeat(64);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest(slug64))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.slug")
                .isEqualTo(slug64);
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 409 for duplicate slug")
        void shouldReturn409ForDuplicateSlug() {
            ensureAdminMembership(workspace);
            persistPractice("taken-slug", "Existing", "cat", true);

            ProblemDetail problem = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("taken-slug"))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Practice slug conflict");
            assertThat(problem.getDetail()).contains("taken-slug");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for invalid slug format (uppercase)")
        void shouldReturn400ForUppercaseSlug() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "INVALID_SLUG",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            ProblemDetail problem = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("slug");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for slug with trailing hyphen")
        void shouldReturn400ForTrailingHyphen() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "bad-slug-",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for slug with consecutive hyphens")
        void shouldReturn400ForConsecutiveHyphens() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "bad--slug",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for slug with leading hyphen")
        void shouldReturn400ForLeadingHyphen() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "-bad-slug",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for invalid trigger events")
        void shouldReturn400ForInvalidTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "valid-slug",
                "Name",
                null,
                "Description",
                List.of("NonExistentEvent"),
                null
            );

            ProblemDetail problem = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("triggerEvents");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for duplicate trigger events")
        void shouldReturn400ForDuplicateTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "dup-events",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated", "PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for blank required fields")
        void shouldReturn400ForBlankFields() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO("", "", null, "", List.of(), null);

            ProblemDetail problem = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKeys("slug", "name", "description", "triggerEvents");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for slug too short")
        void shouldReturn400ForSlugTooShort() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "ab",
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for slug too long (65 chars)")
        void shouldReturn400ForSlugTooLong() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "a".repeat(65),
                "Name",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for name too short")
        void shouldReturn400ForNameTooShort() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "valid-slug",
                "AB",
                null,
                "Description",
                List.of("PullRequestCreated"),
                null
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for empty trigger events list")
        void shouldReturn400ForEmptyTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO("no-events", "Name", null, "Description", List.of(), null);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithMentorUser
        @DisplayName("returns 403 for non-admin user")
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("forbidden-practice"))
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("anon-practice"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /practices/{practiceSlug}")
    class UpdatePractice {

        @Test
        @WithAdminUser
        @DisplayName("partially updates practice (only name)")
        void shouldPartiallyUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("update-me", "Original Name", "original-cat", true);

            var request = new UpdatePracticeRequestDTO("Updated Name", null, null, null, null);

            PracticeDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "update-me")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Updated Name");
            // Verify unchanged fields remain intact
            assertThat(result.category()).isEqualTo("original-cat");
            assertThat(result.description()).isEqualTo("Description for update-me");
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated");
            assertThat(result.detectionPrompt()).isEqualTo("Detect prompt for update-me");
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("fully updates all mutable fields")
        void shouldFullyUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("full-update", "Old Name", "old-cat", true);

            var request = new UpdatePracticeRequestDTO(
                "New Name",
                "new-cat",
                "New description",
                List.of("ReviewSubmitted"),
                "New prompt"
            );

            PracticeDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "full-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("New Name");
            assertThat(result.category()).isEqualTo("new-cat");
            assertThat(result.description()).isEqualTo("New description");
            assertThat(result.triggerEvents()).containsExactly("ReviewSubmitted");
            assertThat(result.detectionPrompt()).isEqualTo("New prompt");

            // Verify database state matches response
            Optional<Practice> persisted = practiceRepository.findByWorkspaceIdAndSlug(
                workspace.getId(),
                "full-update"
            );
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getName()).isEqualTo("New Name");
            assertThat(persisted.get().getCategory()).isEqualTo("new-cat");
            assertThat(persisted.get().getDescription()).isEqualTo("New description");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent slug")
        void shouldReturn404() {
            ensureAdminMembership(workspace);

            var request = new UpdatePracticeRequestDTO("Name", null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "non-existent")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for name too short")
        void shouldReturn400ForNameTooShort() {
            ensureAdminMembership(workspace);
            persistPractice("bad-update", "Name", "cat", true);

            var request = new UpdatePracticeRequestDTO("AB", null, null, null, null);

            ProblemDetail problem = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "bad-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("name");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for whitespace-only name")
        void shouldReturn400ForWhitespaceOnlyName() {
            ensureAdminMembership(workspace);
            persistPractice("ws-name", "Name", "cat", true);

            var request = new UpdatePracticeRequestDTO("   ", null, null, null, null);

            ProblemDetail problem = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "ws-name")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("name");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for whitespace-only description")
        void shouldReturn400ForWhitespaceOnlyDescription() {
            ensureAdminMembership(workspace);
            persistPractice("ws-desc", "Name", "cat", true);

            var request = new UpdatePracticeRequestDTO(null, null, "   ", null, null);

            ProblemDetail problem = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "ws-desc")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Validation failed");
            assertThat(problem.getProperties().get("errors"))
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("description");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for invalid trigger events in update")
        void shouldReturn400ForInvalidTriggerEventsInUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("update-events", "Name", "cat", true);

            var request = new UpdatePracticeRequestDTO(null, null, null, List.of("FakeEvent"), null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "update-events")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithMentorUser
        @DisplayName("returns 403 for non-admin user")
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-update", "Name", "cat", true);

            var request = new UpdatePracticeRequestDTO("New Name", null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "forbidden-update")
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
            var request = new UpdatePracticeRequestDTO("Name", null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "any-slug")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SET ACTIVE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /practices/{practiceSlug}/active")
    class SetActive {

        @Test
        @WithAdminUser
        @DisplayName("sets active to false")
        void shouldSetActiveToFalse() {
            ensureAdminMembership(workspace);
            persistPractice("deactivate-me", "Name", "cat", true);

            PracticeDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "deactivate-me")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.active()).isFalse();

            // Verify database state
            Optional<Practice> persisted = practiceRepository.findByWorkspaceIdAndSlug(
                workspace.getId(),
                "deactivate-me"
            );
            assertThat(persisted).isPresent();
            assertThat(persisted.get().isActive()).isFalse();
        }

        @Test
        @WithAdminUser
        @DisplayName("sets active to true")
        void shouldSetActiveToTrue() {
            ensureAdminMembership(workspace);
            persistPractice("activate-me", "Name", "cat", false);

            PracticeDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "activate-me")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("is idempotent — setting active=true when already true")
        void shouldBeIdempotent() {
            ensureAdminMembership(workspace);
            persistPractice("already-active", "Name", "cat", true);

            PracticeDTO result = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "already-active")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent slug")
        void shouldReturn404() {
            ensureAdminMembership(workspace);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "non-existent")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(false))
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithMentorUser
        @DisplayName("returns 403 for non-admin user")
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-toggle", "Name", "cat", true);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "forbidden-toggle")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(false))
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 400 for null active value")
        void shouldReturn400ForNullActive() {
            ensureAdminMembership(workspace);
            persistPractice("null-active", "Name", "cat", true);

            // Send JSON with null active field
            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "null-active")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"active\": null}")
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "any-slug")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(false))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELETE PRACTICE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DELETE /practices/{practiceSlug}")
    class DeletePractice {

        @Test
        @WithAdminUser
        @DisplayName("deletes a practice and verifies removal from database")
        void shouldDeletePractice() {
            ensureAdminMembership(workspace);
            persistPractice("to-delete", "Delete Me", "cat", true);

            webTestClient
                .delete()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "to-delete")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent();

            // Verify removed from database
            Optional<Practice> persisted = practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), "to-delete");
            assertThat(persisted).isEmpty();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent slug")
        void shouldReturn404() {
            ensureAdminMembership(workspace);

            webTestClient
                .delete()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "non-existent")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithMentorUser
        @DisplayName("returns 403 for non-admin user")
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-delete", "Name", "cat", true);

            webTestClient
                .delete()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "forbidden-delete")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .delete()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "any-slug")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WORKSPACE ISOLATION
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Workspace isolation")
    class WorkspaceIsolation {

        @Test
        @WithAdminUser
        @DisplayName("practice from workspace A is not accessible via workspace B")
        void shouldIsolateReads() {
            User ownerA = persistUser("owner-a");
            User ownerB = persistUser("owner-b");
            Workspace wsA = createWorkspace("ws-a", "WS A", "org-a", AccountType.ORG, ownerA);
            Workspace wsB = createWorkspace("ws-b", "WS B", "org-b", AccountType.ORG, ownerB);
            ensureAdminMembership(wsA);
            ensureAdminMembership(wsB);

            Practice practice = new Practice();
            practice.setWorkspace(wsA);
            practice.setSlug("isolated-practice");
            practice.setName("Isolated");
            practice.setDescription("Description");
            practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceRepository.save(practice);

            // Access via workspace A — should work
            webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", wsA.getWorkspaceSlug(), "isolated-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk();

            // Access via workspace B — should return 404
            webTestClient
                .get()
                .uri(BASE_URI + "/{slug}", wsB.getWorkspaceSlug(), "isolated-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("list returns empty when practices only exist in other workspace")
        void shouldIsolateList() {
            User ownerA = persistUser("list-owner-a");
            User ownerB = persistUser("list-owner-b");
            Workspace wsA = createWorkspace("list-ws-a", "A", "list-org-a", AccountType.ORG, ownerA);
            Workspace wsB = createWorkspace("list-ws-b", "B", "list-org-b", AccountType.ORG, ownerB);
            ensureAdminMembership(wsA);
            ensureAdminMembership(wsB);

            Practice practice = new Practice();
            practice.setWorkspace(wsA);
            practice.setSlug("only-in-a");
            practice.setName("Only in A");
            practice.setDescription("Description");
            practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceRepository.save(practice);

            // List via workspace B — should return empty
            webTestClient
                .get()
                .uri(BASE_URI, wsB.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        @DisplayName("same slug in different workspace is allowed")
        void shouldAllowSameSlugInDifferentWorkspace() {
            User ownerA = persistUser("create-owner-a");
            User ownerB = persistUser("create-owner-b");
            Workspace wsA = createWorkspace("create-ws-a", "A", "create-org-a", AccountType.ORG, ownerA);
            Workspace wsB = createWorkspace("create-ws-b", "B", "create-org-b", AccountType.ORG, ownerB);
            ensureAdminMembership(wsA);
            ensureAdminMembership(wsB);

            // Create practice in workspace A
            webTestClient
                .post()
                .uri(BASE_URI, wsA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("shared-slug"))
                .exchange()
                .expectStatus()
                .isCreated();

            // Same slug in workspace B should also succeed
            webTestClient
                .post()
                .uri(BASE_URI, wsB.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("shared-slug"))
                .exchange()
                .expectStatus()
                .isCreated();
        }

        @Test
        @WithAdminUser
        @DisplayName("cannot update practice via wrong workspace")
        void shouldIsolateWrites() {
            User ownerA = persistUser("write-owner-a");
            User ownerB = persistUser("write-owner-b");
            Workspace wsA = createWorkspace("write-ws-a", "A", "write-org-a", AccountType.ORG, ownerA);
            Workspace wsB = createWorkspace("write-ws-b", "B", "write-org-b", AccountType.ORG, ownerB);
            ensureAdminMembership(wsA);
            ensureAdminMembership(wsB);

            Practice practice = new Practice();
            practice.setWorkspace(wsA);
            practice.setSlug("write-isolated");
            practice.setName("Write Isolated");
            practice.setDescription("Desc");
            practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceRepository.save(practice);

            var request = new UpdatePracticeRequestDTO("Hacked Name", null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", wsB.getWorkspaceSlug(), "write-isolated")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }
    }
}

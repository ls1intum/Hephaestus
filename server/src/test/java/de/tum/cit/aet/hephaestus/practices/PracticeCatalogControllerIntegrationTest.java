package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeActiveRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class PracticeCatalogControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practices";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("catalog-owner");
        workspace = createWorkspace("catalog-ws", "Catalog WS", "catalog-org", AccountType.ORG, owner);
    }

    private Practice persistPractice(String slug, String name, boolean active) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setCriteria("Detect prompt for " + slug);
        practice.setActive(active);
        return practiceRepository.save(practice);
    }

    private CreatePracticeRequestDTO validCreateRequest(String slug) {
        return new CreatePracticeRequestDTO(
            slug,
            "Practice " + slug,
            List.of("PullRequestCreated", "ReviewSubmitted"),
            "Detect if the PR follows best practices",
            null,
            null,
            null,
            null
        );
    }

    // LIST

    @Nested
    class ListPractices {

        @Test
        @WithAdminUser
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
        void shouldReturnAllPractices() {
            ensureAdminMembership(workspace);
            persistPractice("alpha", "Alpha", true);
            persistPractice("beta", "Beta", false);

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
        void shouldReturnOrderedByName() {
            ensureAdminMembership(workspace);
            persistPractice("z-slug", "Zebra", true);
            persistPractice("a-slug", "Alpha", true);
            persistPractice("m-slug", "Middle", true);

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
        void shouldFilterByActive() {
            ensureAdminMembership(workspace);
            persistPractice("active-one", "Active", true);
            persistPractice("inactive-one", "Inactive", false);

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
        @WithMentorUser
        void shouldAllowMemberToList() {
            User member = persistUser("mentor");
            ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("member-visible", "Visible", true);

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

    // GET SINGLE

    @Nested
    class GetPractice {

        @Test
        @WithAdminUser
        @DisplayName("returns practice by slug with all fields")
        void shouldReturnPractice() {
            ensureAdminMembership(workspace);
            persistPractice("target-practice", "Target Practice", true);

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
            assertThat(result.active()).isTrue();
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated");
            assertThat(result.criteria()).isEqualTo("Detect prompt for target-practice");
            assertThat(result.createdAt()).isNotNull();
            assertThat(result.updatedAt()).isNotNull();
        }

        @Test
        @WithMentorUser
        void shouldAllowMemberToGet() {
            User member = persistUser("mentor");
            ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("member-get", "Member Get", true);

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

    // CREATE

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
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated", "ReviewSubmitted");
            assertThat(result.criteria()).isEqualTo("Detect if the PR follows best practices");
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
            assertThat(persisted.get().isActive()).isTrue();
        }

        @Test
        @WithAdminUser
        void shouldCreatePracticeWithMinimalFields() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "minimal-practice",
                "Minimal Practice",
                List.of("PullRequestCreated"),
                "Minimal criteria",
                null,
                null,
                null,
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
            assertThat(result.criteria()).isEqualTo("Minimal criteria");
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
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
        void shouldReturn409ForDuplicateSlug() {
            ensureAdminMembership(workspace);
            persistPractice("taken-slug", "Existing", true);

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

        @ParameterizedTest(name = "rejects invalid slug \"{0}\"")
        @MethodSource("invalidSlugs")
        @WithAdminUser
        void shouldReturn400ForInvalidSlug(String badSlug) {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                badSlug,
                "Name",
                List.of("PullRequestCreated"),
                null,
                null,
                null,
                null,
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

        static Stream<String> invalidSlugs() {
            return Stream.of(
                "INVALID_SLUG", // uppercase + underscore
                "bad-slug-", // trailing hyphen
                "bad--slug", // consecutive hyphens
                "-bad-slug", // leading hyphen
                "ab", // too short (< 3 chars)
                "a".repeat(65) // too long (> 64 chars)
            );
        }

        @Test
        @WithAdminUser
        void shouldReturn400ForInvalidTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "valid-slug",
                "Name",
                List.of("NonExistentEvent"),
                null,
                null,
                null,
                null,
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
        void shouldReturn400ForDuplicateTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "dup-events",
                "Name",
                List.of("PullRequestCreated", "PullRequestCreated"),
                null,
                null,
                null,
                null,
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
        void shouldReturn400ForBlankFields() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO("", "", List.of(), null, null, null, null, null);

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
                .containsKeys("slug", "name", "criteria", "triggerEvents");
        }

        @Test
        @WithAdminUser
        void shouldReturn400ForNameTooShort() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "valid-slug",
                "AB",
                List.of("PullRequestCreated"),
                null,
                null,
                null,
                null,
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
        void shouldReturn400ForEmptyTriggerEvents() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO("no-events", "Name", List.of(), null, null, null, null, null);

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
        @DisplayName("rejects anonymous create (403 via CSRF gate, before auth)")
        void shouldRejectAnonymousCreate() {
            // Anonymous POST → double-submit CSRF gate (ADR 0017) rejects 403 before auth (no
            // X-XSRF-TOKEN). The create stays blocked for anonymous callers.
            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("anon-practice"))
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    // UPDATE

    @Nested
    @DisplayName("PATCH /practices/{practiceSlug}")
    class UpdatePractice {

        @Test
        @WithAdminUser
        @DisplayName("partially updates practice (only name)")
        void shouldPartiallyUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("update-me", "Original Name", true);

            var request = new UpdatePracticeRequestDTO("Updated Name", null, null, null, null, null, null);

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
            assertThat(result.triggerEvents()).containsExactly("PullRequestCreated");
            assertThat(result.criteria()).isEqualTo("Detect prompt for update-me");
            assertThat(result.active()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("fully updates all mutable fields")
        void shouldFullyUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("full-update", "Old Name", true);

            var request = new UpdatePracticeRequestDTO(
                "New Name",
                List.of("ReviewSubmitted"),
                "New prompt",
                null,
                null,
                null,
                null
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
            assertThat(result.triggerEvents()).containsExactly("ReviewSubmitted");
            assertThat(result.criteria()).isEqualTo("New prompt");

            // Verify database state matches response
            Optional<Practice> persisted = practiceRepository.findByWorkspaceIdAndSlug(
                workspace.getId(),
                "full-update"
            );
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getName()).isEqualTo("New Name");
            assertThat(persisted.get().getCriteria()).isEqualTo("New prompt");
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent slug")
        void shouldReturn404() {
            ensureAdminMembership(workspace);

            var request = new UpdatePracticeRequestDTO("Name", null, null, null, null, null, null);

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
        void shouldReturn400ForNameTooShort() {
            ensureAdminMembership(workspace);
            persistPractice("bad-update", "Name", true);

            var request = new UpdatePracticeRequestDTO("AB", null, null, null, null, null, null);

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
        void shouldReturn400ForWhitespaceOnlyName() {
            ensureAdminMembership(workspace);
            persistPractice("ws-name", "Name", true);

            var request = new UpdatePracticeRequestDTO("   ", null, null, null, null, null, null);

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
        void shouldReturn400ForWhitespaceOnlyCriteria() {
            ensureAdminMembership(workspace);
            persistPractice("ws-criteria", "Name", true);

            var request = new UpdatePracticeRequestDTO(null, null, "   ", null, null, null, null);

            ProblemDetail problem = webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "ws-criteria")
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
                .containsKey("criteria");
        }

        @Test
        @WithAdminUser
        void shouldReturn400ForInvalidTriggerEventsInUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("update-events", "Name", true);

            var request = new UpdatePracticeRequestDTO(null, List.of("FakeEvent"), null, null, null, null, null);

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
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-update", "Name", true);

            var request = new UpdatePracticeRequestDTO("New Name", null, null, null, null, null, null);

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
            var request = new UpdatePracticeRequestDTO("Name", null, null, null, null, null, null);

            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // SET ACTIVE

    @Nested
    @DisplayName("PATCH /practices/{practiceSlug}/active")
    class SetActive {

        @Test
        @WithAdminUser
        void shouldSetActiveToFalse() {
            ensureAdminMembership(workspace);
            persistPractice("deactivate-me", "Name", true);

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
        void shouldSetActiveToTrue() {
            ensureAdminMembership(workspace);
            persistPractice("activate-me", "Name", false);

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
            persistPractice("already-active", "Name", true);

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
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-toggle", "Name", true);

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
        void shouldReturn400ForNullActive() {
            ensureAdminMembership(workspace);
            persistPractice("null-active", "Name", true);

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
            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}/active", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePracticeActiveRequestDTO(false))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // DELETE PRACTICE

    @Nested
    class DeletePractice {

        @Test
        @WithAdminUser
        @DisplayName("deletes a practice and verifies removal from database")
        void shouldDeletePractice() {
            ensureAdminMembership(workspace);
            persistPractice("to-delete", "Delete Me", true);

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
        void shouldReturn403ForNonAdmin() {
            User memberUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, memberUser, WorkspaceMembership.WorkspaceRole.MEMBER);
            persistPractice("forbidden-delete", "Name", true);

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
            // Pass CSRF so the auth layer (not the CSRF filter) answers a cookie-style write → 401 (ADR 0017).
            String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
            webTestClient
                .delete()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "any-slug")
                .headers(TestAuthUtils.withCsrf(csrf))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // WORKSPACE ISOLATION

    @Nested
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
            practice.setCriteria("Description");
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
            practice.setCriteria("Description");
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
            practice.setCriteria("Desc");
            practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceRepository.save(practice);

            var request = new UpdatePracticeRequestDTO("Hacked Name", null, null, null, null, null, null);

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

    // PRACTICE VERSIONING (SCD-2)

    @Nested
    @DisplayName("Practice criteria versioning (SCD-2)")
    class CriteriaVersioning {

        private List<PracticeRevision> revisionsFor(String slug) {
            Long practiceId = practiceRepository
                .findByWorkspaceIdAndSlug(workspace.getId(), slug)
                .orElseThrow()
                .getId();
            return practiceRevisionRepository
                .findAll()
                .stream()
                .filter(r -> r.getPractice().getId().equals(practiceId))
                .sorted((a, b) -> Integer.compare(a.getRevisionNumber(), b.getRevisionNumber()))
                .toList();
        }

        @Test
        @WithAdminUser
        @DisplayName("create appends revision 1 snapshotting the criteria")
        void createAppendsRevisionOne() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("versioned-practice"))
                .exchange()
                .expectStatus()
                .isCreated();

            List<PracticeRevision> revisions = revisionsFor("versioned-practice");
            assertThat(revisions).hasSize(1);
            assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(1);
            assertThat(revisions.get(0).getCriteria()).isEqualTo("Detect if the PR follows best practices");
            assertThat(revisions.get(0).getCreatedAt()).isNotNull();
        }

        @Test
        @WithAdminUser
        @DisplayName("update with CHANGED criteria appends revision 2")
        void changedCriteriaAppendsRevisionTwo() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("evolving-practice"))
                .exchange()
                .expectStatus()
                .isCreated();

            var request = new UpdatePracticeRequestDTO(
                null,
                null,
                "A revised detection rubric",
                null,
                null,
                null,
                null
            );

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "evolving-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

            List<PracticeRevision> revisions = revisionsFor("evolving-practice");
            assertThat(revisions).hasSize(2);
            assertThat(revisions).extracting(PracticeRevision::getRevisionNumber).containsExactly(1, 2);
            assertThat(revisions.get(0).getCriteria()).isEqualTo("Detect if the PR follows best practices");
            assertThat(revisions.get(1).getCriteria()).isEqualTo("A revised detection rubric");
        }

        @Test
        @WithAdminUser
        @DisplayName("update that does NOT change criteria appends no new revision")
        void unchangedCriteriaAppendsNoRevision() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("stable-practice"))
                .exchange()
                .expectStatus()
                .isCreated();

            // Patch only the name — criteria is untouched (null in the PATCH body).
            var request = new UpdatePracticeRequestDTO("Renamed Practice", null, null, null, null, null, null);

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "stable-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

            List<PracticeRevision> revisions = revisionsFor("stable-practice");
            assertThat(revisions).hasSize(1);
            assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(1);
        }

        @Test
        @WithAdminUser
        @DisplayName("update sending the SAME criteria value appends no new revision")
        void identicalCriteriaValueAppendsNoRevision() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateRequest("noop-criteria-practice"))
                .exchange()
                .expectStatus()
                .isCreated();

            // Resend the exact same criteria text — the service compares values, so this is a no-op revision-wise.
            var request = new UpdatePracticeRequestDTO(
                null,
                null,
                "Detect if the PR follows best practices",
                null,
                null,
                null,
                null
            );

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "noop-criteria-practice")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

            assertThat(revisionsFor("noop-criteria-practice")).hasSize(1);
        }
    }

    // LEARNER ANTI-LEAK PROJECTION

    @Nested
    @DisplayName("GET /practices/learner — anti-leak projection")
    class LearnerProjection {

        @Test
        @WithAdminUser
        @DisplayName("raw JSON omits criteria but carries why-it-matters and what-good-looks-like")
        void learnerViewHidesCriteriaExposesRationale() {
            ensureAdminMembership(workspace);

            var request = new CreatePracticeRequestDTO(
                "learner-practice",
                "Learner Practice",
                List.of("PullRequestCreated"),
                "INTERNAL detection rubric — must never reach a learner",
                null,
                null,
                "Small, focused PRs are easier to review.",
                "A PR that changes one thing and explains why in the description."
            );

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated();

            String rawJson = webTestClient
                .get()
                .uri(BASE_URI + "/learner", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

            assertThat(rawJson).isNotNull();
            // Anti-leak invariant: the detection rubric must be physically absent from the learner payload.
            assertThat(rawJson).doesNotContain("\"criteria\"");
            assertThat(rawJson).doesNotContain("INTERNAL detection rubric");
            // The learner-facing rationale + exemplar ARE present with their exact values.
            assertThat(rawJson).contains("whyItMatters");
            assertThat(rawJson).contains("whatGoodLooksLike");
            assertThat(rawJson).contains("Small, focused PRs are easier to review.");
            assertThat(rawJson).contains("A PR that changes one thing and explains why in the description.");
        }
    }

    // AUTHORING GUARD — detector vocabulary must not leak into learner-facing copy

    @Nested
    @DisplayName("Authoring guard on whatGoodLooksLike")
    class AuthoringGuard {

        private CreatePracticeRequestDTO createWithExemplar(String slug, String whatGoodLooksLike) {
            return new CreatePracticeRequestDTO(
                slug,
                "Guard Practice",
                List.of("PullRequestCreated"),
                "Detect prompt",
                null,
                null,
                "Why it matters.",
                whatGoodLooksLike
            );
        }

        @Test
        @WithAdminUser
        @DisplayName("create with OBSERVED in whatGoodLooksLike → 400")
        void rejectsObservedTokenOnCreate() {
            ensureAdminMembership(workspace);

            ProblemDetail problem = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createWithExemplar("guard-observed", "The result is OBSERVED in every case."))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            // The guard's IllegalArgumentException is mapped to 400 by the workspace-scoped advice.
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getTitle()).isEqualTo("Invalid workspace request");
            // Nothing persisted.
            assertThat(practiceRepository.findByWorkspaceIdAndSlug(workspace.getId(), "guard-observed")).isEmpty();
        }

        @Test
        @WithAdminUser
        @DisplayName("create with NOT_OBSERVED in whatGoodLooksLike → 400")
        void rejectsNotObservedTokenOnCreate() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createWithExemplar("guard-not-observed", "Flagged as NOT_OBSERVED by the detector."))
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("create with NOT_APPLICABLE in whatGoodLooksLike → 400")
        void rejectsNotApplicableTokenOnCreate() {
            ensureAdminMembership(workspace);

            webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createWithExemplar("guard-not-applicable", "Marked NOT_APPLICABLE here."))
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("update that introduces OBSERVED into whatGoodLooksLike → 400")
        void rejectsObservedTokenOnUpdate() {
            ensureAdminMembership(workspace);
            persistPractice("guard-update", "Guard Update", true);

            var request = new UpdatePracticeRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                "This is OBSERVED behaviour."
            );

            webTestClient
                .patch()
                .uri(BASE_URI + "/{slug}", workspace.getWorkspaceSlug(), "guard-update")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("clean whatGoodLooksLike succeeds")
        void acceptsCleanExemplar() {
            ensureAdminMembership(workspace);

            PracticeDTO result = webTestClient
                .post()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    createWithExemplar(
                        "guard-clean",
                        "A PR description that states the problem, the change, and how it was verified."
                    )
                )
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(PracticeDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("guard-clean");
        }
    }
}

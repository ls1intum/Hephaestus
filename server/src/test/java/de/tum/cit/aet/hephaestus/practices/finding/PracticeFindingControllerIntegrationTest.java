package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class PracticeFindingControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASE_URI = "/workspaces/{workspaceSlug}/practices/findings";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace workspace;
    private Practice practiceA;
    private Practice practiceB;
    private AgentJob agentJob;
    private User developer; // login = "testuser" to match @WithUser

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("finding-owner");
        workspace = createWorkspace("finding-ws", "Finding WS", "finding-org", AccountType.ORG, owner);

        // Create developer with login "testuser" matching @WithUser default
        developer = persistUser("testuser");
        ensureWorkspaceMembership(workspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);

        practiceA = persistPractice("pr-description-quality", "PR Description Quality");
        practiceB = persistPractice("code-review-thoroughness", "Code Review Thoroughness");

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice persistPractice(String slug, String name) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Description for " + slug);
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setActive(true);
        return practiceRepository.save(practice);
    }

    private UUID insertFinding(
        Practice practice,
        User user,
        String title,
        String verdict,
        String severity,
        float confidence,
        String artifactType,
        Long artifactId,
        Instant detectedAt
    ) {
        UUID id = UUID.randomUUID();
        practiceFindingRepository.insertIfAbsent(
            id,
            "key-" + id,
            agentJob.getId(),
            practice.getId(),
            null, // practiceRevisionId — pre-versioning marker
            artifactType,
            artifactId,
            user.getId(),
            user.getId(),
            title,
            verdict,
            severity,
            confidence,
            null,
            "Test reasoning for " + title,
            "Test guidance for " + title,
            null,
            detectedAt
        );
        return id;
    }

    // GET /practices/findings

    @Nested
    class ListFindings {

        @Test
        @WithUser
        void shouldReturnEmptyPage() {
            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(0)
                .jsonPath("$.totalElements")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        void shouldReturnOnlyOwnFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "My finding", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            // Other user's finding should NOT appear
            User otherUser = persistUser("other-user");
            insertFinding(
                practiceA,
                otherUser,
                "Other finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.8f,
                "PULL_REQUEST",
                2L,
                now
            );

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("My finding")
                .jsonPath("$.totalElements")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        void shouldFilterByPracticeSlug() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "Practice A", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceB, developer, "Practice B", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "?practiceSlug={slug}", workspace.getWorkspaceSlug(), practiceA.getSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("Practice A")
                .jsonPath("$.content[0].practiceSlug")
                .isEqualTo("pr-description-quality");
        }

        @Test
        @WithUser
        void shouldFilterByVerdict() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "Good", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceA, developer, "Bad", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "?verdict=NOT_OBSERVED", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("Bad");
        }

        @Test
        @WithUser
        void shouldFilterByPracticeSlugAndVerdict() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "A pos", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceA, developer, "A neg", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);
            insertFinding(practiceB, developer, "B neg", "NOT_OBSERVED", "MINOR", 0.7f, "PULL_REQUEST", 3L, now);

            webTestClient
                .get()
                .uri(
                    BASE_URI + "?practiceSlug={slug}&verdict=NOT_OBSERVED",
                    workspace.getWorkspaceSlug(),
                    practiceA.getSlug()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("A neg")
                .jsonPath("$.content[0].practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$.content[0].verdict")
                .isEqualTo("NOT_OBSERVED");
        }

        @Test
        @WithUser
        @DisplayName("respects pagination")
        void shouldPaginate() {
            Instant base = Instant.now();
            for (int i = 0; i < 5; i++) {
                insertFinding(
                    practiceA,
                    developer,
                    "Finding " + i,
                    "OBSERVED",
                    "INFO",
                    0.9f,
                    "PULL_REQUEST",
                    (long) (i + 1),
                    base.minus(i, ChronoUnit.HOURS)
                );
            }

            webTestClient
                .get()
                .uri(BASE_URI + "?page=0&size=2", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(2)
                .jsonPath("$.totalElements")
                .isEqualTo(5)
                .jsonPath("$.totalPages")
                .isEqualTo(3)
                .jsonPath("$.number")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        void shouldCapPageSize() {
            insertFinding(practiceA, developer, "Single", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, Instant.now());

            webTestClient
                .get()
                .uri(BASE_URI + "?size=999", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.size")
                .isEqualTo(100);
        }

        @Test
        @WithUser
        @DisplayName("normalizes negative page to 0 and zero/negative size to 1")
        void shouldNormalizeBoundaryPaginationValues() {
            insertFinding(
                practiceA,
                developer,
                "Boundary",
                "OBSERVED",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                Instant.now()
            );

            webTestClient
                .get()
                .uri(BASE_URI + "?page=-1&size=0", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.number")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(1)
                .jsonPath("$.content.length()")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        void shouldReturnCorrectShapeWithoutInternalFields() {
            Instant now = Instant.now();
            insertFinding(
                practiceA,
                developer,
                "Shape check",
                "NOT_OBSERVED",
                "MAJOR",
                0.85f,
                "PULL_REQUEST",
                42L,
                now
            );

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                // Expected fields present
                .jsonPath("$.content[0].id")
                .isNotEmpty()
                .jsonPath("$.content[0].practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$.content[0].practiceName")
                .isEqualTo("PR Description Quality")
                .jsonPath("$.content[0].artifactType")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$.content[0].artifactId")
                .isEqualTo(42)
                .jsonPath("$.content[0].title")
                .isEqualTo("Shape check")
                .jsonPath("$.content[0].verdict")
                .isEqualTo("NOT_OBSERVED")
                .jsonPath("$.content[0].severity")
                .isEqualTo("MAJOR")
                .jsonPath("$.content[0].confidence")
                .isEqualTo(0.85)
                .jsonPath("$.content[0].detectedAt")
                .isNotEmpty()
                // Internal fields must not leak
                .jsonPath("$.content[0].agentJobId")
                .doesNotExist()
                .jsonPath("$.content[0].idempotencyKey")
                .doesNotExist()
                .jsonPath("$.content[0].evidence")
                .doesNotExist()
                .jsonPath("$.content[0].reasoning")
                .doesNotExist()
                .jsonPath("$.content[0].guidance")
                .doesNotExist();
        }

        @Test
        void shouldReturn401ForUnauthenticated() {
            webTestClient.get().uri(BASE_URI, workspace.getWorkspaceSlug()).exchange().expectStatus().isUnauthorized();
        }

        @Test
        @WithUser
        @DisplayName("orders findings by detected_at descending")
        void shouldOrderByDetectedAtDesc() {
            Instant now = Instant.now();
            insertFinding(
                practiceA,
                developer,
                "Oldest",
                "OBSERVED",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                now.minus(2, ChronoUnit.HOURS)
            );
            insertFinding(practiceA, developer, "Newest", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);
            insertFinding(
                practiceA,
                developer,
                "Middle",
                "OBSERVED",
                "INFO",
                0.7f,
                "PULL_REQUEST",
                3L,
                now.minus(1, ChronoUnit.HOURS)
            );

            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].title")
                .isEqualTo("Newest")
                .jsonPath("$.content[1].title")
                .isEqualTo("Middle")
                .jsonPath("$.content[2].title")
                .isEqualTo("Oldest");
        }

        @Test
        @WithUser
        void shouldNotReturnFindingsFromDifferentWorkspace() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "My WS finding", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            // Create a second workspace with its own practice and finding
            User otherOwner = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace(
                "other-ws",
                "Other WS",
                "other-org",
                AccountType.ORG,
                otherOwner
            );
            ensureWorkspaceMembership(otherWorkspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);

            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Desc");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice.setActive(true);
            otherPractice = practiceRepository.save(otherPractice);

            AgentJob otherJob = new AgentJob();
            otherJob.setWorkspace(otherWorkspace);
            otherJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            otherJob = agentJobRepository.save(otherJob);

            UUID otherFindingId = UUID.randomUUID();
            practiceFindingRepository.insertIfAbsent(
                otherFindingId,
                "key-" + otherFindingId,
                otherJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                developer.getId(),
                developer.getId(),
                "Other WS finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.8f,
                null,
                "reasoning",
                "guidance",
                null,
                now
            );

            // Query the first workspace — should only see "My WS finding"
            webTestClient
                .get()
                .uri(BASE_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("My WS finding")
                .jsonPath("$.totalElements")
                .isEqualTo(1);
        }
    }

    // GET /practices/findings/summary

    @Nested
    class GetSummary {

        @Test
        @WithUser
        void shouldReturnEmptyList() {
            webTestClient
                .get()
                .uri(BASE_URI + "/summary", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        void shouldReturnCorrectCountsAndFields() {
            Instant now = Instant.now();
            Instant oldest = now.minus(2, ChronoUnit.HOURS);
            insertFinding(practiceA, developer, "A pos 1", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(
                practiceA,
                developer,
                "A pos 2",
                "OBSERVED",
                "INFO",
                0.8f,
                "PULL_REQUEST",
                2L,
                now.minus(1, ChronoUnit.HOURS)
            );
            insertFinding(practiceA, developer, "A neg 1", "NOT_OBSERVED", "MAJOR", 0.7f, "PULL_REQUEST", 3L, oldest);
            insertFinding(
                practiceB,
                developer,
                "B neg 1",
                "NOT_OBSERVED",
                "MINOR",
                0.6f,
                "PULL_REQUEST",
                4L,
                now.minus(3, ChronoUnit.HOURS)
            );

            webTestClient
                .get()
                .uri(BASE_URI + "/summary", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                // Results ordered by practice name ASC
                .jsonPath("$[0].practiceSlug")
                .isEqualTo("code-review-thoroughness")
                .jsonPath("$[0].practiceName")
                .isEqualTo("Code Review Thoroughness")
                .jsonPath("$[0].totalFindings")
                .isEqualTo(1)
                .jsonPath("$[0].observedCount")
                .isEqualTo(0)
                .jsonPath("$[0].notObservedCount")
                .isEqualTo(1)
                .jsonPath("$[0].lastFindingAt")
                .isNotEmpty()
                .jsonPath("$[1].practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$[1].practiceName")
                .isEqualTo("PR Description Quality")
                .jsonPath("$[1].totalFindings")
                .isEqualTo(3)
                .jsonPath("$[1].observedCount")
                .isEqualTo(2)
                .jsonPath("$[1].notObservedCount")
                .isEqualTo(1)
                .jsonPath("$[1].lastFindingAt")
                .isNotEmpty();
        }

        @Test
        void shouldReturn401ForUnauthenticated() {
            webTestClient
                .get()
                .uri(BASE_URI + "/summary", workspace.getWorkspaceSlug())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithUser
        void shouldExcludeOtherUsersFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "Mine", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            User otherUser = persistUser("someone-else");
            insertFinding(practiceA, otherUser, "Theirs", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "/summary", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].totalFindings")
                .isEqualTo(1)
                .jsonPath("$[0].observedCount")
                .isEqualTo(1);
        }
    }

    // GET /practices/findings/{findingId}

    @Nested
    class GetFindingDetail {

        @Test
        @WithUser
        void shouldReturnDetailForOwnFinding() {
            Instant now = Instant.now();
            UUID findingId = insertFinding(
                practiceA,
                developer,
                "Detailed finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.85f,
                "PULL_REQUEST",
                42L,
                now
            );

            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", workspace.getWorkspaceSlug(), findingId)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(findingId.toString())
                .jsonPath("$.title")
                .isEqualTo("Detailed finding")
                .jsonPath("$.verdict")
                .isEqualTo("NOT_OBSERVED")
                .jsonPath("$.severity")
                .isEqualTo("MAJOR")
                .jsonPath("$.confidence")
                .isEqualTo(0.85)
                .jsonPath("$.practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$.practiceName")
                .isEqualTo("PR Description Quality")
                .jsonPath("$.artifactType")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$.artifactId")
                .isEqualTo(42)
                .jsonPath("$.reasoning")
                .isEqualTo("Test reasoning for Detailed finding")
                .jsonPath("$.guidance")
                .isEqualTo("Test guidance for Detailed finding")
                .jsonPath("$.detectedAt")
                .isNotEmpty()
                // Internal fields must not leak
                .jsonPath("$.agentJobId")
                .doesNotExist()
                .jsonPath("$.idempotencyKey")
                .doesNotExist();
        }

        @Test
        @WithUser
        void shouldReturn404ForOtherUserFinding() {
            User otherUser = persistUser("other-developer");
            UUID otherId = insertFinding(
                practiceA,
                otherUser,
                "Not mine",
                "OBSERVED",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                Instant.now()
            );

            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", workspace.getWorkspaceSlug(), otherId)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        void shouldReturn401ForUnauthenticated() {
            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", workspace.getWorkspaceSlug(), UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithUser
        void shouldReturn404ForNonExistentFinding() {
            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", workspace.getWorkspaceSlug(), UUID.randomUUID())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithUser
        void shouldReturnEvidenceWhenPresent() {
            UUID findingId = UUID.randomUUID();
            String evidenceJson = "{\"locations\":[{\"file\":\"README.md\",\"line\":42}]}";
            practiceFindingRepository.insertIfAbsent(
                findingId,
                "key-" + findingId,
                agentJob.getId(),
                practiceA.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                50L,
                developer.getId(),
                developer.getId(),
                "Evidence finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.9f,
                evidenceJson,
                "reasoning",
                "guidance",
                null,
                Instant.now()
            );

            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", workspace.getWorkspaceSlug(), findingId)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.evidence")
                .isNotEmpty()
                .jsonPath("$.evidence.locations[0].file")
                .isEqualTo("README.md")
                .jsonPath("$.evidence.locations[0].line")
                .isEqualTo(42);
        }

        @Test
        @WithUser
        void shouldReturn404ForFindingInDifferentWorkspace() {
            // Create finding in current workspace
            UUID findingId = insertFinding(
                practiceA,
                developer,
                "WS1 finding",
                "OBSERVED",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                Instant.now()
            );

            // Create a different workspace
            User otherOwner = persistUser("ws2-owner");
            Workspace otherWorkspace = createWorkspace(
                "other-ws2",
                "Other WS2",
                "other-org2",
                AccountType.ORG,
                otherOwner
            );
            ensureWorkspaceMembership(otherWorkspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);

            // Try to access finding from workspace1 via workspace2's URL
            webTestClient
                .get()
                .uri(BASE_URI + "/{findingId}", otherWorkspace.getWorkspaceSlug(), findingId)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }
    }

    // GET /practices/findings/pull-request/{prId}

    @Nested
    class GetPullRequestFindings {

        @Test
        @WithUser
        @DisplayName("returns all findings for a pull request")
        void shouldReturnPrFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "PR finding 1", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 100L, now);
            insertFinding(
                practiceB,
                developer,
                "PR finding 2",
                "NOT_OBSERVED",
                "MAJOR",
                0.8f,
                "PULL_REQUEST",
                100L,
                now.minus(1, ChronoUnit.HOURS)
            );

            // Different PR — should not appear
            insertFinding(practiceA, developer, "Other PR", "OBSERVED", "INFO", 0.7f, "PULL_REQUEST", 200L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 100L)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].title")
                .isEqualTo("PR finding 1")
                .jsonPath("$[1].title")
                .isEqualTo("PR finding 2");
        }

        @Test
        @WithUser
        void shouldIncludeOtherUsersFindingsForSamePr() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "My PR finding", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 100L, now);

            User otherUser = persistUser("pr-collaborator");
            insertFinding(
                practiceA,
                otherUser,
                "Their PR finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.8f,
                "PULL_REQUEST",
                100L,
                now.minus(1, ChronoUnit.HOURS)
            );

            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 100L)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].title")
                .isEqualTo("My PR finding")
                .jsonPath("$[0].verdict")
                .isEqualTo("OBSERVED")
                .jsonPath("$[1].title")
                .isEqualTo("Their PR finding")
                .jsonPath("$[1].verdict")
                .isEqualTo("NOT_OBSERVED");
        }

        @Test
        void shouldReturn401ForUnauthenticated() {
            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 999L)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithUser
        void shouldReturnEmptyForUnknownPr() {
            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 999L)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("orders findings by detected_at descending")
        void shouldOrderByDetectedAtDesc() {
            Instant now = Instant.now();
            insertFinding(
                practiceA,
                developer,
                "Old",
                "OBSERVED",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                100L,
                now.minus(2, ChronoUnit.HOURS)
            );
            insertFinding(practiceB, developer, "New", "NOT_OBSERVED", "MAJOR", 0.8f, "PULL_REQUEST", 100L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 100L)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].title")
                .isEqualTo("New")
                .jsonPath("$[1].title")
                .isEqualTo("Old");
        }

        @Test
        @WithUser
        void shouldNotReturnPrFindingsFromDifferentWorkspace() {
            Instant now = Instant.now();
            insertFinding(practiceA, developer, "WS1 PR finding", "OBSERVED", "INFO", 0.9f, "PULL_REQUEST", 100L, now);

            // Create second workspace with its own practice and finding for same PR ID
            User otherOwner = persistUser("ws2-pr-owner");
            Workspace otherWorkspace = createWorkspace("ws2-pr", "WS2 PR", "ws2-org", AccountType.ORG, otherOwner);
            ensureWorkspaceMembership(otherWorkspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);

            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("ws2-practice");
            otherPractice.setName("WS2 Practice");
            otherPractice.setCriteria("Desc");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice.setActive(true);
            otherPractice = practiceRepository.save(otherPractice);

            AgentJob otherJob = new AgentJob();
            otherJob.setWorkspace(otherWorkspace);
            otherJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            otherJob = agentJobRepository.save(otherJob);

            UUID ws2FindingId = UUID.randomUUID();
            practiceFindingRepository.insertIfAbsent(
                ws2FindingId,
                "key-" + ws2FindingId,
                otherJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                100L,
                developer.getId(),
                developer.getId(),
                "WS2 PR finding",
                "NOT_OBSERVED",
                "MAJOR",
                0.8f,
                null,
                "reasoning",
                "guidance",
                null,
                now
            );

            // Query workspace1 — should only see WS1 finding
            webTestClient
                .get()
                .uri(BASE_URI + "/pull-request/{prId}", workspace.getWorkspaceSlug(), 100L)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].title")
                .isEqualTo("WS1 PR finding");
        }
    }
}

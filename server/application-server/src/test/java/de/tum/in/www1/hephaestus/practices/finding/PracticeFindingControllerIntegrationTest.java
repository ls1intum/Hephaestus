package de.tum.in.www1.hephaestus.practices.finding;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
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
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Practice finding controller integration")
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
    private User contributor; // login = "testuser" to match @WithUser

    private User createGitLabContributor(String login, long nativeId) {
        return TestUserFactory.ensureUser(userRepository, login, nativeId, ensureGitLabProvider());
    }

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("finding-owner");
        workspace = createWorkspace("finding-ws", "Finding WS", "finding-org", AccountType.ORG, owner);

        // Create contributor with login "testuser" matching @WithUser default
        contributor = persistUser("testuser");
        ensureWorkspaceMembership(workspace, contributor, WorkspaceMembership.WorkspaceRole.MEMBER);

        practiceA = persistPractice("pr-description-quality", "PR Description Quality", "pr-quality");
        practiceB = persistPractice("code-review-thoroughness", "Code Review Thoroughness", "review");

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice persistPractice(String slug, String name, String category) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCategory(category);
        practice.setDescription("Description for " + slug);
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
        String targetType,
        Long targetId,
        Instant detectedAt
    ) {
        UUID id = UUID.randomUUID();
        practiceFindingRepository.insertIfAbsent(
            id,
            "key-" + id,
            agentJob.getId(),
            practice.getId(),
            targetType,
            targetId,
            user.getId(),
            title,
            verdict,
            severity,
            confidence,
            null,
            "Test reasoning for " + title,
            "Test guidance for " + title,
            detectedAt
        );
        return id;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /practices/findings
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices/findings")
    class ListFindings {

        @Test
        @WithUser
        @DisplayName("returns empty page when no findings exist")
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
        @DisplayName("returns only current user's findings")
        void shouldReturnOnlyOwnFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "My finding", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            // Other user's finding should NOT appear
            User otherUser = persistUser("other-user");
            insertFinding(practiceA, otherUser, "Other finding", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

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
        @DisplayName("filters by practice slug")
        void shouldFilterByPracticeSlug() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "Practice A", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceB, contributor, "Practice B", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

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
        @DisplayName("filters by verdict")
        void shouldFilterByVerdict() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "Good", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceA, contributor, "Bad", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

            webTestClient
                .get()
                .uri(BASE_URI + "?verdict=NEGATIVE", workspace.getWorkspaceSlug())
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
        @DisplayName("filters by practice slug AND verdict combined")
        void shouldFilterByPracticeSlugAndVerdict() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "A pos", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(practiceA, contributor, "A neg", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);
            insertFinding(practiceB, contributor, "B neg", "NEGATIVE", "MINOR", 0.7f, "PULL_REQUEST", 3L, now);

            webTestClient
                .get()
                .uri(
                    BASE_URI + "?practiceSlug={slug}&verdict=NEGATIVE",
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
                .isEqualTo("NEGATIVE");
        }

        @Test
        @WithUser
        @DisplayName("respects pagination")
        void shouldPaginate() {
            Instant base = Instant.now();
            for (int i = 0; i < 5; i++) {
                insertFinding(
                    practiceA,
                    contributor,
                    "Finding " + i,
                    "POSITIVE",
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
        @DisplayName("caps page size at 100")
        void shouldCapPageSize() {
            insertFinding(
                practiceA,
                contributor,
                "Single",
                "POSITIVE",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                Instant.now()
            );

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
                contributor,
                "Boundary",
                "POSITIVE",
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
        @DisplayName("does not leak internal fields and returns complete list-item shape")
        void shouldReturnCorrectShapeWithoutInternalFields() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "Shape check", "NEGATIVE", "MAJOR", 0.85f, "PULL_REQUEST", 42L, now);

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
                .jsonPath("$.content[0].category")
                .isEqualTo("pr-quality")
                .jsonPath("$.content[0].targetType")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$.content[0].targetId")
                .isEqualTo(42)
                .jsonPath("$.content[0].title")
                .isEqualTo("Shape check")
                .jsonPath("$.content[0].verdict")
                .isEqualTo("NEGATIVE")
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
        @DisplayName("returns 401 for unauthenticated request")
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
                contributor,
                "Oldest",
                "POSITIVE",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                1L,
                now.minus(2, ChronoUnit.HOURS)
            );
            insertFinding(practiceA, contributor, "Newest", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);
            insertFinding(
                practiceA,
                contributor,
                "Middle",
                "POSITIVE",
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
        @DisplayName("does not return findings from a different workspace")
        void shouldNotReturnFindingsFromDifferentWorkspace() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "My WS finding", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            // Create a second workspace with its own practice and finding
            User otherOwner = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace(
                "other-ws",
                "Other WS",
                "other-org",
                AccountType.ORG,
                otherOwner
            );
            ensureWorkspaceMembership(otherWorkspace, contributor, WorkspaceMembership.WorkspaceRole.MEMBER);

            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCategory("other");
            otherPractice.setDescription("Desc");
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
                "PULL_REQUEST",
                2L,
                contributor.getId(),
                "Other WS finding",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                null,
                "reasoning",
                "guidance",
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

    // ══════════════════════════════════════════════════════════════════════════
    // GET /practices/findings/summary
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices/findings/summary")
    class GetSummary {

        @Test
        @WithUser
        @DisplayName("returns empty list when no findings exist")
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
        @DisplayName("returns per-practice aggregation with correct counts and all fields")
        void shouldReturnCorrectCountsAndFields() {
            Instant now = Instant.now();
            Instant oldest = now.minus(2, ChronoUnit.HOURS);
            insertFinding(practiceA, contributor, "A pos 1", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(
                practiceA,
                contributor,
                "A pos 2",
                "POSITIVE",
                "INFO",
                0.8f,
                "PULL_REQUEST",
                2L,
                now.minus(1, ChronoUnit.HOURS)
            );
            insertFinding(practiceA, contributor, "A neg 1", "NEGATIVE", "MAJOR", 0.7f, "PULL_REQUEST", 3L, oldest);
            insertFinding(
                practiceB,
                contributor,
                "B neg 1",
                "NEGATIVE",
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
                .jsonPath("$[0].category")
                .isEqualTo("review")
                .jsonPath("$[0].totalFindings")
                .isEqualTo(1)
                .jsonPath("$[0].positiveCount")
                .isEqualTo(0)
                .jsonPath("$[0].negativeCount")
                .isEqualTo(1)
                .jsonPath("$[0].lastFindingAt")
                .isNotEmpty()
                .jsonPath("$[1].practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$[1].practiceName")
                .isEqualTo("PR Description Quality")
                .jsonPath("$[1].category")
                .isEqualTo("pr-quality")
                .jsonPath("$[1].totalFindings")
                .isEqualTo(3)
                .jsonPath("$[1].positiveCount")
                .isEqualTo(2)
                .jsonPath("$[1].negativeCount")
                .isEqualTo(1)
                .jsonPath("$[1].lastFindingAt")
                .isNotEmpty();
        }

        @Test
        @DisplayName("returns 401 for unauthenticated request")
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
        @DisplayName("excludes other users' findings from summary")
        void shouldExcludeOtherUsersFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "Mine", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);

            User otherUser = persistUser("someone-else");
            insertFinding(practiceA, otherUser, "Theirs", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 2L, now);

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
                .jsonPath("$[0].positiveCount")
                .isEqualTo(1);
        }

        @Test
        @WithUser(username = "testuser", githubId = 1L, gitlabId = 18024L)
        @DisplayName("includes findings from a linked non-primary contributor row in summary")
        void shouldIncludeLinkedContributorRowsInSummary() {
            Instant now = Instant.now();
            User gitlabContributor = createGitLabContributor("testuser-gl", 18024L);
            insertFinding(practiceA, contributor, "GitHub finding", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 1L, now);
            insertFinding(
                practiceA,
                gitlabContributor,
                "GitLab finding",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                "PULL_REQUEST",
                2L,
                now.minus(1, ChronoUnit.HOURS)
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
                .isEqualTo(1)
                .jsonPath("$[0].totalFindings")
                .isEqualTo(2)
                .jsonPath("$[0].positiveCount")
                .isEqualTo(1)
                .jsonPath("$[0].negativeCount")
                .isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /practices/findings/{findingId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices/findings/{findingId}")
    class GetFindingDetail {

        @Test
        @WithUser
        @DisplayName("returns full detail for own finding")
        void shouldReturnDetailForOwnFinding() {
            Instant now = Instant.now();
            UUID findingId = insertFinding(
                practiceA,
                contributor,
                "Detailed finding",
                "NEGATIVE",
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
                .isEqualTo("NEGATIVE")
                .jsonPath("$.severity")
                .isEqualTo("MAJOR")
                .jsonPath("$.confidence")
                .isEqualTo(0.85)
                .jsonPath("$.practiceSlug")
                .isEqualTo("pr-description-quality")
                .jsonPath("$.practiceName")
                .isEqualTo("PR Description Quality")
                .jsonPath("$.category")
                .isEqualTo("pr-quality")
                .jsonPath("$.targetType")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$.targetId")
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
        @DisplayName("returns 404 for other user's finding")
        void shouldReturn404ForOtherUserFinding() {
            User otherUser = persistUser("other-contributor");
            UUID otherId = insertFinding(
                practiceA,
                otherUser,
                "Not mine",
                "POSITIVE",
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
        @WithUser(username = "testuser", githubId = 1L, gitlabId = 18024L)
        @DisplayName("returns detail for finding owned by a linked non-primary contributor row")
        void shouldReturnDetailForLinkedContributorRow() {
            User gitlabContributor = createGitLabContributor("testuser-gl", 18024L);
            UUID findingId = insertFinding(
                practiceA,
                gitlabContributor,
                "Linked-row detail",
                "NEGATIVE",
                "MAJOR",
                0.85f,
                "PULL_REQUEST",
                42L,
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
                .jsonPath("$.id")
                .isEqualTo(findingId.toString())
                .jsonPath("$.title")
                .isEqualTo("Linked-row detail");
        }

        @Test
        @DisplayName("returns 401 for unauthenticated request")
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
        @DisplayName("returns 404 for non-existent finding")
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
        @DisplayName("returns evidence JSON in detail view when present")
        void shouldReturnEvidenceWhenPresent() {
            UUID findingId = UUID.randomUUID();
            String evidenceJson = "{\"locations\":[{\"file\":\"README.md\",\"line\":42}]}";
            practiceFindingRepository.insertIfAbsent(
                findingId,
                "key-" + findingId,
                agentJob.getId(),
                practiceA.getId(),
                "PULL_REQUEST",
                50L,
                contributor.getId(),
                "Evidence finding",
                "NEGATIVE",
                "MAJOR",
                0.9f,
                evidenceJson,
                "reasoning",
                "guidance",
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
        @DisplayName("returns 404 for finding in different workspace")
        void shouldReturn404ForFindingInDifferentWorkspace() {
            // Create finding in current workspace
            UUID findingId = insertFinding(
                practiceA,
                contributor,
                "WS1 finding",
                "POSITIVE",
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
            ensureWorkspaceMembership(otherWorkspace, contributor, WorkspaceMembership.WorkspaceRole.MEMBER);

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

    // ══════════════════════════════════════════════════════════════════════════
    // GET /practices/findings/pull-request/{prId}
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /practices/findings/pull-request/{prId}")
    class GetPullRequestFindings {

        @Test
        @WithUser
        @DisplayName("returns all findings for a pull request")
        void shouldReturnPrFindings() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "PR finding 1", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 100L, now);
            insertFinding(
                practiceB,
                contributor,
                "PR finding 2",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                "PULL_REQUEST",
                100L,
                now.minus(1, ChronoUnit.HOURS)
            );

            // Different PR — should not appear
            insertFinding(practiceA, contributor, "Other PR", "POSITIVE", "INFO", 0.7f, "PULL_REQUEST", 200L, now);

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
        @DisplayName("includes other users' findings for same PR")
        void shouldIncludeOtherUsersFindingsForSamePr() {
            Instant now = Instant.now();
            insertFinding(practiceA, contributor, "My PR finding", "POSITIVE", "INFO", 0.9f, "PULL_REQUEST", 100L, now);

            User otherUser = persistUser("pr-collaborator");
            insertFinding(
                practiceA,
                otherUser,
                "Their PR finding",
                "NEGATIVE",
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
                .isEqualTo("POSITIVE")
                .jsonPath("$[1].title")
                .isEqualTo("Their PR finding")
                .jsonPath("$[1].verdict")
                .isEqualTo("NEGATIVE");
        }

        @Test
        @DisplayName("returns 401 for unauthenticated request")
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
        @DisplayName("returns empty list for unknown PR")
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
                contributor,
                "Old",
                "POSITIVE",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                100L,
                now.minus(2, ChronoUnit.HOURS)
            );
            insertFinding(practiceB, contributor, "New", "NEGATIVE", "MAJOR", 0.8f, "PULL_REQUEST", 100L, now);

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
        @DisplayName("does not return PR findings from a different workspace")
        void shouldNotReturnPrFindingsFromDifferentWorkspace() {
            Instant now = Instant.now();
            insertFinding(
                practiceA,
                contributor,
                "WS1 PR finding",
                "POSITIVE",
                "INFO",
                0.9f,
                "PULL_REQUEST",
                100L,
                now
            );

            // Create second workspace with its own practice and finding for same PR ID
            User otherOwner = persistUser("ws2-pr-owner");
            Workspace otherWorkspace = createWorkspace("ws2-pr", "WS2 PR", "ws2-org", AccountType.ORG, otherOwner);
            ensureWorkspaceMembership(otherWorkspace, contributor, WorkspaceMembership.WorkspaceRole.MEMBER);

            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("ws2-practice");
            otherPractice.setName("WS2 Practice");
            otherPractice.setCategory("other");
            otherPractice.setDescription("Desc");
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
                "PULL_REQUEST",
                100L,
                contributor.getId(),
                "WS2 PR finding",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                null,
                "reasoning",
                "guidance",
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

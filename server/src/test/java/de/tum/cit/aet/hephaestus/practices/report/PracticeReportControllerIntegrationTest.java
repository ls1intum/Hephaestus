package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessAuditPort;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.HealthAvailability;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.HealthVisibility;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Who may read each report surface, and — the part only this tier can prove — that a named read lands a row
 * on the disclosure trail.
 */
class PracticeReportControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORTS_URI = "/workspaces/{workspaceSlug}/practices/reports";
    private static final String HEALTH_URI = "/workspaces/{workspaceSlug}/practices/health";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeAreaRepository practiceAreaRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Spied, not mocked: every other test needs the real recorder so the trail assertions mean something,
     * and one test needs it to fail.
     */
    @MockitoSpyBean
    private DataAccessAuditPort dataAccessAudit;

    private Workspace workspace;
    private Practice practice;
    private Practice secondPractice;
    private AgentJob agentJob;
    private User developer; // login "testuser", to match @WithUser
    private User admin; // login "admin", to match ensureAdminMembership
    private User otherDeveloper;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("report-owner");
        workspace = createWorkspace("report-ws", "Report WS", "report-org", AccountType.ORG, owner);

        developer = persistUser("testuser");
        ensureWorkspaceMembership(workspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);
        otherDeveloper = persistUser("other-dev");
        ensureWorkspaceMembership(workspace, otherDeveloper, WorkspaceMembership.WorkspaceRole.MEMBER);
        admin = ensureAdminMembership(workspace).getUser();

        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug("reviewing");
        area.setName("Reviewing");
        area.setActive(true);
        area = practiceAreaRepository.save(area);

        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setArea(area);
        practice.setSlug("asking-for-review");
        practice.setName("Asking for review");
        practice.setCriteria("Criteria that never reach a learner");
        practice.setWhyItMatters("Reviews catch what you cannot see in your own diff");
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setActive(true);
        practice = practiceRepository.save(practice);
        secondPractice = practiceRepository.save(practiceIn(area, "reviewing-thoroughly", "Reviewing thoroughly"));

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice practiceIn(PracticeArea area, String slug, String name) {
        Practice created = new Practice();
        created.setWorkspace(workspace);
        created.setArea(area);
        created.setSlug(slug);
        created.setName(name);
        created.setCriteria("Criteria that never reach a learner");
        created.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        created.setActive(true);
        return created;
    }

    /** A confident problem for {@code subject} — one that clears the quarantine floor and is displayed. */
    private void insertProblem(User subject, long artifactId) {
        insertObservation(practice, subject, artifactId, "Opened without a description", "ABSENT", "BAD", "MAJOR");
    }

    /** A strength for {@code subject} on the second practice. */
    private void insertStrength(User subject, long artifactId) {
        insertObservation(secondPractice, subject, artifactId, "Reviewed thoroughly", "PRESENT", "GOOD", "INFO");
    }

    private void insertObservation(
        Practice target,
        User subject,
        long artifactId,
        String title,
        String presence,
        String assessment,
        String severity
    ) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            agentJob.getId(),
            target.getId(),
            null,
            "PULL_REQUEST",
            artifactId,
            subject.getId(),
            title,
            presence,
            assessment,
            severity,
            0.95f,
            null,
            "Test reasoning",
            null,
            Instant.now().minus(1, ChronoUnit.DAYS)
        );
    }

    private long disclosureRows(String resourceType) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM data_access_event WHERE workspace_id = ? AND resource_type = ?",
            Long.class,
            workspace.getId(),
            resourceType
        );
        return count == null ? 0 : count;
    }

    @Nested
    @DisplayName("GET /practices/reports/me")
    class SelfReport {

        @Test
        @WithUser
        @DisplayName("a member reads their own cards, and nothing is disclosed")
        void memberReadsOwnReport() {
            insertProblem(developer, 42L);
            insertProblem(developer, 43L);

            List<PracticeReportCardDTO> cards = webTestClient
                .get()
                .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(PracticeReportCardDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.slug()).isEqualTo("asking-for-review");
                    assertThat(card.status()).isEqualTo(PracticeStatus.DEVELOPING);
                    assertThat(card.trend()).isEqualTo(PracticeTrend.NEW);
                    assertThat(card.toWorkOn()).hasSize(2);
                });
            assertThat(disclosureRows("PRACTICE_REPORT")).isZero();
        }

        @Test
        @DisplayName("the wire shape carries status, trend and the two item lists")
        @WithUser
        void wireShapeIsStable() {
            insertProblem(developer, 42L);
            insertStrength(developer, 43L);

            webTestClient
                .get()
                .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].slug")
                .isEqualTo(practice.getSlug())
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].trend")
                .isEqualTo("NEW")
                .jsonPath("$[0].toWorkOn[0].title")
                .isEqualTo("Opened without a description")
                .jsonPath("$[0].toWorkOn[0].severity")
                .isEqualTo("MAJOR")
                .jsonPath("$[0].strengths.length()")
                .isEqualTo(0)
                .jsonPath("$[1].slug")
                .isEqualTo(secondPractice.getSlug())
                .jsonPath("$[1].status")
                .isEqualTo("STRENGTH")
                .jsonPath("$[1].strengths[0].title")
                .isEqualTo("Reviewed thoroughly")
                .jsonPath("$[1].toWorkOn.length()")
                .isEqualTo(0);
        }

        @Test
        @DisplayName("an unauthenticated caller gets 401")
        void unauthenticatedIsRejected() {
            webTestClient
                .get()
                .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithMentorUser
        @DisplayName("a non-member on a public workspace is forbidden")
        void nonMemberIsForbidden() {
            persistUser("mentor");
            workspace.setIsPubliclyViewable(true);
            workspaceRepository.save(workspace);

            webTestClient
                .get()
                .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /practices/reports")
    class Roster {

        @Test
        @WithUser
        @DisplayName("a member cannot list the roster")
        void memberCannotListRoster() {
            webTestClient
                .get()
                .uri(REPORTS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithAdminUser
        @DisplayName("out-of-range paging is clamped, not a 500")
        void outOfRangePagingIsClamped() {
            insertProblem(developer, 42L);

            webTestClient
                .get()
                .uri(builder ->
                    builder
                        .path(REPORTS_URI)
                        .queryParam("page", -1)
                        .queryParam("size", 0)
                        .build(workspace.getWorkspaceSlug())
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk();
        }

        @Test
        @WithAdminUser
        @DisplayName("an admin lists the roster and the read is disclosed")
        void adminListsRosterAndIsAudited() {
            insertProblem(developer, 42L);

            List<PracticeReportSummaryDTO> roster = webTestClient
                .get()
                .uri(REPORTS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(PracticeReportSummaryDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(roster)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.userLogin()).isEqualTo("testuser");
                    assertThat(entry.needsAttention()).isTrue();
                    assertThat(entry.areas())
                        .singleElement()
                        .satisfies(cell -> {
                            assertThat(cell.areaSlug()).isEqualTo("reviewing");
                            assertThat(cell.status()).isEqualTo(PracticeStatus.DEVELOPING);
                        });
                });
            assertThat(disclosureRows("PRACTICE_ROSTER")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /practices/reports/{userId}")
    class DrillDown {

        @Test
        @WithAdminUser
        @DisplayName("a drill-down disclosure names both parties")
        void adminDrillDownIsAudited() {
            insertProblem(developer, 42L);

            webTestClient
                .get()
                .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), developer.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(PracticeReportCardDTO.class)
                .hasSize(1);

            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_user_id, subject_user_id, resource_type FROM data_access_event WHERE workspace_id = ?",
                workspace.getId()
            );
            assertThat(row.get("actor_user_id")).isEqualTo(admin.getId());
            assertThat(row.get("subject_user_id")).isEqualTo(developer.getId());
            assertThat(row.get("resource_type")).isEqualTo("PRACTICE_REPORT");
        }

        @Test
        @WithAdminUser
        @DisplayName("an unknown subject is 404 and discloses nothing")
        void unknownSubjectIsNotFound() {
            webTestClient
                .get()
                .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), otherDeveloper.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();

            assertThat(disclosureRows("PRACTICE_REPORT")).isZero();
        }

        @Test
        @WithAdminUser
        @DisplayName("a read that cannot be recorded is refused, not served unrecorded")
        void unrecordableDisclosureIsRefused() {
            insertProblem(developer, 42L);
            doThrow(new IllegalStateException("trail unavailable"))
                .when(dataAccessAudit)
                .recordDisclosure(any(), any(), any(), any());

            webTestClient
                .get()
                .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), developer.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .is5xxServerError();
        }

        @Test
        @WithUser
        @DisplayName("a member cannot drill into a colleague")
        void memberCannotDrillDown() {
            insertProblem(otherDeveloper, 42L);

            webTestClient
                .get()
                .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), otherDeveloper.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /practices/health")
    class Health {

        @Test
        @WithUser
        @DisplayName("a member is refused under MENTORS_ONLY")
        void memberDeniedUnderMentorsOnly() {
            webTestClient
                .get()
                .uri(HEALTH_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithUser
        @DisplayName("a member sees the aggregate under EVERYONE, suppressed on a small team")
        void memberSeesSuppressedAggregateUnderEveryone() {
            insertProblem(developer, 42L);
            workspace.getFeatures().setHealthVisibility(HealthVisibility.EVERYONE);
            workspaceRepository.save(workspace);

            List<AreaHealthDTO> cards = webTestClient
                .get()
                .uri(HEALTH_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(AreaHealthDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.availability()).isEqualTo(HealthAvailability.SUPPRESSED);
                    assertThat(card.strengthCount()).isNull();
                    assertThat(card.developingCount()).isNull();
                });
        }

        @Test
        @WithAdminUser
        @DisplayName("an admin sees the counts on the same small team")
        void adminSeesCountsOnASmallTeam() {
            insertProblem(developer, 42L);

            List<AreaHealthDTO> cards = webTestClient
                .get()
                .uri(HEALTH_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(AreaHealthDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.availability()).isEqualTo(HealthAvailability.AVAILABLE);
                    assertThat(card.developingCount()).isEqualTo(1);
                    assertThat(card.strengthCount()).isZero();
                });
        }
    }
}

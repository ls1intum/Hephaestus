package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.core.audit.DataAccessAuditWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Controller-level integration tests for the mentor overview. Exercises access control (roster/drill-down are
 * admin/owner-only; workspace health follows the visibility tier), the recency window, and the disclosure
 * audit rows written on drill-down/roster views.
 */
class PracticeReportControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORTS_URI = "/workspaces/{workspaceSlug}/practices/reports";
    private static final String HEALTH_URI = "/workspaces/{workspaceSlug}/practices/health";
    private static final String REVIEW_AREA = "constructive-code-review";

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
    private DataAccessAuditWriter dataAccessAuditWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    private Workspace workspace;
    private Practice reviewPractice;
    private AgentJob agentJob;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("overview-owner");
        workspace = createWorkspace("overview-ws", "Overview WS", "overview-org", AccountType.ORG, owner);

        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(REVIEW_AREA);
        area.setName("Constructive code review");
        area.setDisplayOrder(0);
        area = practiceAreaRepository.save(area);

        reviewPractice = persistPractice("leaves-useful-specific-review-comments", "Useful review comments", area);

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice persistPractice(String slug, String name, PracticeArea area) {
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(slug);
        practice.setName(name);
        practice.setCriteria("Criteria for " + slug);
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setActive(true);
        practice.setArea(area);
        return practiceRepository.save(practice);
    }

    private void setVisibility(HealthVisibility tier) {
        workspace.getFeatures().setHealthVisibility(tier);
        workspace = workspaceRepository.save(workspace);
    }

    private void insertObservation(
        Practice practice,
        User user,
        String title,
        String presence,
        String severity,
        Long artifactId,
        Instant observedAt
    ) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            agentJob.getId(),
            practice.getId(),
            null,
            "PULL_REQUEST",
            artifactId,
            user.getId(),
            title,
            presence,
            "PRESENT".equals(presence) ? "GOOD" : "BAD",
            severity,
            0.9f,
            null,
            "reasoning",
            null,
            observedAt
        );
    }

    // ---- /roster access control (admin/owner only) ----

    @Test
    @WithAdminUser
    @DisplayName("admin sees the roster")
    void adminSeesRoster() {
        User dev = persistUser("dev-one");
        ensureWorkspaceMembership(workspace, dev, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureAdminMembership(workspace);
        insertObservation(reviewPractice, dev, "Vague comment", "ABSENT", "MAJOR", 1L, Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].userId")
            .isEqualTo(dev.getId().intValue())
            .jsonPath("$[0].userLogin")
            .isEqualTo("dev-one")
            .jsonPath("$[0].needsAttention")
            .isEqualTo(true);
    }

    @Test
    @WithUser
    @DisplayName("member gets 403 on the roster")
    void memberForbiddenOnRoster() {
        User dev = getMemberUser();
        insertObservation(reviewPractice, dev, "Vague comment", "ABSENT", "MAJOR", 1L, Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    // ---- /health visibility tier ----

    @Test
    @WithUser
    @DisplayName("member gets 403 on workspace health when tier is MENTORS_ONLY")
    void memberForbiddenOnHealthMentorOnly() {
        getMemberUser();
        setVisibility(HealthVisibility.MENTORS_ONLY);

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
    @DisplayName("member sees workspace health when tier is EVERYONE")
    void memberSeesHealthWorkspaceVisible() {
        getMemberUser();
        setVisibility(HealthVisibility.EVERYONE);

        webTestClient
            .get()
            .uri(HEALTH_URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            // One area, zero active developers this window -> NO_DATA card (not a k-anonymity suppression:
            // there is nobody to re-identify), counts null.
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].areaSlug")
            .isEqualTo(REVIEW_AREA)
            .jsonPath("$[0].availability")
            .isEqualTo("NO_DATA")
            .jsonPath("$[0].strengthCount")
            .doesNotExist();
    }

    // ---- drill-down / roster disclosure audit rows ----

    @Test
    @WithAdminUser
    @DisplayName("drill-down by an admin writes a PRACTICE_REPORT disclosure row (actor=admin, subject=path user)")
    void drillDownWritesAuditRow() {
        User admin = ensureAdminMembership(workspace).getUser();
        User subject = persistUser("subject-dev");
        ensureWorkspaceMembership(workspace, subject, WorkspaceMembership.WorkspaceRole.MEMBER);
        insertObservation(reviewPractice, subject, "A gap", "ABSENT", "MAJOR", 1L, Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), subject.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();

        List<Map<String, Object>> audit = jdbcTemplate.queryForList(
            "SELECT workspace_id, actor_user_id, subject_user_id, resource_type FROM data_access_event"
        );
        org.assertj.core.api.Assertions.assertThat(audit).hasSize(1);
        Map<String, Object> row = audit.get(0);
        org.assertj.core.api.Assertions.assertThat(row.get("resource_type")).isEqualTo("PRACTICE_REPORT");
        org.assertj.core.api.Assertions.assertThat(((Number) row.get("workspace_id")).longValue()).isEqualTo(
            workspace.getId()
        );
        org.assertj.core.api.Assertions.assertThat(((Number) row.get("actor_user_id")).longValue()).isEqualTo(
            admin.getId()
        );
        org.assertj.core.api.Assertions.assertThat(((Number) row.get("subject_user_id")).longValue()).isEqualTo(
            subject.getId()
        );
    }

    @Test
    @WithAdminUser
    @DisplayName("listing the roster writes a PRACTICE_ROSTER disclosure row (actor=admin, subject=NULL bulk view)")
    void rosterWritesAuditRow() {
        User admin = ensureAdminMembership(workspace).getUser();
        User dev = persistUser("roster-dev");
        ensureWorkspaceMembership(workspace, dev, WorkspaceMembership.WorkspaceRole.MEMBER);
        insertObservation(reviewPractice, dev, "A gap", "ABSENT", "MAJOR", 1L, Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI, workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();

        List<Map<String, Object>> audit = jdbcTemplate.queryForList(
            "SELECT actor_user_id, subject_user_id, resource_type FROM data_access_event"
        );
        org.assertj.core.api.Assertions.assertThat(audit).hasSize(1);
        Map<String, Object> row = audit.get(0);
        org.assertj.core.api.Assertions.assertThat(row.get("resource_type")).isEqualTo("PRACTICE_ROSTER");
        org.assertj.core.api.Assertions.assertThat(((Number) row.get("actor_user_id")).longValue()).isEqualTo(
            admin.getId()
        );
        // A bulk view discloses many subjects at once — no single subject.
        org.assertj.core.api.Assertions.assertThat(row.get("subject_user_id")).isNull();
    }

    @Test
    @WithAdminUser
    @DisplayName("drill-down rejects non-roster subjects before writing an audit row")
    void drillDownRejectsUnknownSubjectWithoutAuditRow() {
        ensureAdminMembership(workspace);
        User unrelated = persistUser("not-in-roster");

        webTestClient
            .get()
            .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), unrelated.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();

        Integer auditRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM data_access_event", Integer.class);
        org.assertj.core.api.Assertions.assertThat(auditRows).isZero();
    }

    @Test
    @WithUser
    @DisplayName("member gets 403 on the per-developer drill-down")
    void memberForbiddenOnDrillDown() {
        getMemberUser();
        User other = persistUser("someone");

        webTestClient
            .get()
            .uri(REPORTS_URI + "/{userId}", workspace.getWorkspaceSlug(), other.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    // ---- self report (GET /practices/reports/me) ----

    @Test
    @DisplayName("unauthenticated request to the self report is 401")
    void myReportRequiresAuthentication() {
        webTestClient
            .get()
            .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithUser
    @DisplayName("self report honors the review-cycle window: in-window appears, older excluded")
    void reflectionHonorsWindow() {
        User dev = getMemberUser();
        // Inside the just-closed weekly window (recent) and far outside it (60 days ago).
        insertObservation(reviewPractice, dev, "Recent gap", "ABSENT", "MAJOR", 1L, Instant.now());
        insertObservation(
            reviewPractice,
            dev,
            "Ancient gap",
            "ABSENT",
            "MAJOR",
            2L,
            Instant.now().minus(60, ChronoUnit.DAYS)
        );

        webTestClient
            .get()
            .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].slug")
            .isEqualTo(reviewPractice.getSlug())
            .jsonPath("$[0].toWorkOn.length()")
            .isEqualTo(1)
            .jsonPath("$[0].toWorkOn[0].title")
            .isEqualTo("Recent gap");
    }

    @Test
    @WithUser
    @DisplayName("self report items are anchored to their PR: title, deep link, repository and state are served")
    void reflectionItemsCarryArtifactContext() {
        User dev = getMemberUser();
        var provider = ensureGitHubProvider();
        Repository repo = new Repository();
        repo.setNativeId(9001L);
        repo.setProvider(provider);
        repo.setName("payments-api");
        repo.setNameWithOwner("acme/payments-api");
        repo.setHtmlUrl("https://github.com/acme/payments-api");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        PullRequest pullRequest = new PullRequest();
        pullRequest.setNativeId(9002L);
        pullRequest.setProvider(provider);
        pullRequest.setNumber(575);
        pullRequest.setTitle("Add distance warnings to the AR recorder");
        pullRequest.setHtmlUrl("https://github.com/acme/payments-api/pull/575");
        pullRequest.setState(Issue.State.MERGED);
        pullRequest.setRepository(repo);
        pullRequest.setCreatedAt(Instant.now());
        pullRequest.setUpdatedAt(Instant.now());
        pullRequest = pullRequestRepository.save(pullRequest);
        insertObservation(reviewPractice, dev, "A gap", "ABSENT", "MAJOR", pullRequest.getId(), Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].toWorkOn[0].artifactTitle")
            .isEqualTo("Add distance warnings to the AR recorder")
            .jsonPath("$[0].toWorkOn[0].artifactUrl")
            .isEqualTo("https://github.com/acme/payments-api/pull/575")
            .jsonPath("$[0].toWorkOn[0].artifactNumber")
            .isEqualTo(575)
            .jsonPath("$[0].toWorkOn[0].artifactRepository")
            .isEqualTo("acme/payments-api")
            .jsonPath("$[0].toWorkOn[0].artifactState")
            .isEqualTo("MERGED")
            .jsonPath("$[0].toWorkOn[0].observedAt")
            .exists();
    }

    @Test
    @WithUser
    @DisplayName("an observation whose artifact no longer resolves is served without a deep link, never failing")
    void reflectionToleratesUnresolvableArtifact() {
        User dev = getMemberUser();
        insertObservation(reviewPractice, dev, "A gap", "ABSENT", "MAJOR", 424242L, Instant.now());

        webTestClient
            .get()
            .uri(REPORTS_URI + "/me", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].toWorkOn[0].artifactTitle")
            .doesNotExist()
            .jsonPath("$[0].toWorkOn[0].artifactUrl")
            .doesNotExist();
    }

    /** The @WithUser default login is "testuser"; ensure it is a member with a persisted User row. */
    private User getMemberUser() {
        User dev = persistUser("testuser");
        ensureWorkspaceMembership(workspace, dev, WorkspaceMembership.WorkspaceRole.MEMBER);
        return dev;
    }
}

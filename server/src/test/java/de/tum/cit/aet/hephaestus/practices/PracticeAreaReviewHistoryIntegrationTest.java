package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional coverage for {@code GET /practice-areas/{areaSlug}/review-history} — the developer-facing record of
 * what each review run saw.
 *
 * <p>The grain is the point: a run is returned whole or not at all, so the two queries behind it (which runs,
 * then their observations) must agree. And an undecided observation has to survive to the payload — this surface is
 * the inspectable record, so a practice that ran and hedged must not read like one that never ran.
 */
class PracticeAreaReviewHistoryIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HISTORY_URI = "/workspaces/{workspaceSlug}/practice-areas/{areaSlug}/review-history";

    /** Delivery authorization reads the run's evidence contract, so every fixture observation cites a source. */
    private static final String DIFF_EVIDENCE_JSON =
        "{\"citations\":[{\"sourceKind\":\"scm.pull-request.diff\",\"artifactPath\":\"inputs/context/diff.patch\"," +
        "\"path\":\"src/Main.java\",\"side\":\"NEW\",\"startLine\":42,\"endLine\":42,\"quote\":\"example\"," +
        "\"quoteRedacted\":false}]}";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeAreaRepository areaRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace workspace;
    private PracticeArea area;
    private Practice practice;
    private AgentJob agentJob;
    private User developer; // login = "testuser" to match @WithUser

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("review-history-owner");
        workspace = createWorkspace(
            "review-history-ws",
            "Review History WS",
            "review-history-org",
            AccountType.ORG,
            owner
        );

        developer = persistUser("testuser");
        ensureWorkspaceMembership(workspace, developer, WorkspaceMembership.WorkspaceRole.MEMBER);

        area = persistArea(workspace, "code-quality", "Code Quality");
        practice = persistPractice(workspace, area, "pr-description-quality", "PR Description Quality");
        agentJob = persistAgentJob(workspace);
    }

    private PracticeArea persistArea(Workspace ws, String slug, String name) {
        PracticeArea a = new PracticeArea();
        a.setWorkspace(ws);
        a.setSlug(slug);
        a.setName(name);
        return areaRepository.save(a);
    }

    private Practice persistPractice(Workspace ws, PracticeArea boundArea, String slug, String name) {
        Practice p = new Practice();
        p.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        p.setWorkspace(ws);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Description for " + slug);
        p.setArea(boundArea);
        p = practiceRepository.saveAndFlush(p);
        p.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(p, 1)));
        return practiceRepository.saveAndFlush(p);
    }

    private AgentJob persistAgentJob(Workspace ws) {
        AgentJob job = new AgentJob();
        job.setWorkspace(ws);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        job.setEvidenceSnapshot(OBJECT_MAPPER.valueToTree(Map.of("manifest", Map.of("contractVersion", "1.0.0"))));
        return agentJobRepository.save(job);
    }

    private void insertFinding(
        String title,
        String presence,
        @org.jspecify.annotations.Nullable String assessment,
        @org.jspecify.annotations.Nullable String severity,
        String artifactKind,
        Long artifactId
    ) {
        insertFinding(
            practice,
            agentJob,
            title,
            presence,
            assessment,
            severity,
            artifactKind,
            artifactId,
            Instant.now()
        );
    }

    private void insertFinding(
        Practice observedPractice,
        AgentJob reviewJob,
        String title,
        String presence,
        @org.jspecify.annotations.Nullable String assessment,
        @org.jspecify.annotations.Nullable String severity,
        String artifactKind,
        Long artifactId,
        Instant observedAt
    ) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            reviewJob.getId(),
            observedPractice.getId(),
            observedPractice.getCurrentRevision().getId(),
            artifactKind,
            artifactId,
            developer.getId(),
            title,
            presence,
            assessment,
            severity,
            DIFF_EVIDENCE_JSON,
            "Test reasoning for " + title,
            null,
            observedAt,
            "LIVE"
        );
    }

    private WebTestClient.BodyContentSpec getHistory() {
        return webTestClient
            .get()
            .uri(HISTORY_URI, workspace.getWorkspaceSlug(), area.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody();
    }

    @Test
    @WithUser
    @DisplayName("returns a review run whole, with every observation that explains it")
    void shouldReturnCompleteRun() {
        insertFinding("Motivation is clear", "PRESENT", "GOOD", null, ArtifactKinds.PULL_REQUEST.value(), 1L);
        insertFinding("No testing notes", "ABSENT", "BAD", "MAJOR", ArtifactKinds.PULL_REQUEST.value(), 1L);

        getHistory()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].reviewId")
            .isEqualTo(agentJob.getId().toString())
            .jsonPath("$.content[0].observations.length()")
            .isEqualTo(2);
    }

    @Test
    @WithUser
    @DisplayName("carries an undecided observation with a null assessment rather than dropping it")
    void shouldCarryInconclusiveFindingWithoutAnAssessment() {
        insertFinding("Could not tell from the diff", "INCONCLUSIVE", null, null, "scm.pull_request", 1L);

        getHistory()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].observations.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].observations[0].presence")
            .isEqualTo("INCONCLUSIVE")
            .jsonPath("$.content[0].observations[0].assessment")
            .doesNotExist();
    }

    @Test
    @WithUser
    @DisplayName("an unfiltered request is not silently narrowed to pull requests")
    void shouldNotDefaultToPullRequestsWhenNoKindFilterIsGiven() {
        insertFinding("Issue lacks acceptance criteria", "ABSENT", "BAD", "MINOR", ArtifactKinds.ISSUE.value(), 7L);

        getHistory()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].observations.length()")
            .isEqualTo(1)
            .jsonPath("$.content[0].observations[0].title")
            .isEqualTo("Issue lacks acceptance criteria");
    }

    @Test
    @WithUser
    @DisplayName("a run that produced nothing to judge is not a moment in the history")
    void shouldOmitRunsWithoutAnythingToJudge() {
        insertFinding("Nothing to judge here", "NOT_APPLICABLE", null, null, ArtifactKinds.PULL_REQUEST.value(), 1L);

        getHistory().jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    @WithUser
    void shouldSelectRunsByMatchingSeverityWithoutTreatingStrengthsAsMatches() {
        insertFinding("Motivation is clear", "PRESENT", "GOOD", null, ArtifactKinds.PULL_REQUEST.value(), 1L);

        webTestClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .path(HISTORY_URI)
                    .queryParam("severities", "MAJOR")
                    .build(workspace.getWorkspaceSlug(), area.getSlug())
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(0);
    }

    @Test
    @WithUser
    void shouldFillAPageAfterWithholdingANewerRun() {
        AgentJob olderJob = persistAgentJob(workspace);
        insertFinding(
            practice,
            olderJob,
            "Visible observation",
            "PRESENT",
            "GOOD",
            null,
            ArtifactKinds.PULL_REQUEST.value(),
            1L,
            Instant.parse("2025-01-01T00:00:00Z")
        );

        Practice superseded = persistPractice(workspace, area, "superseded", "Superseded");
        AgentJob newerJob = persistAgentJob(workspace);
        insertFinding(
            superseded,
            newerJob,
            "Withheld observation",
            "PRESENT",
            "GOOD",
            null,
            ArtifactKinds.PULL_REQUEST.value(),
            2L,
            Instant.parse("2025-01-02T00:00:00Z")
        );
        superseded.setCriteria("New criteria");
        superseded.setArea(area);
        superseded.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(superseded, 2)));
        practiceRepository.saveAndFlush(superseded);

        webTestClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.path(HISTORY_URI).queryParam("size", 1).build(workspace.getWorkspaceSlug(), area.getSlug())
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content[0].observations[0].title")
            .isEqualTo("Visible observation")
            .jsonPath("$.hasNext")
            .isEqualTo(false);
    }

    @Test
    @WithUser
    @DisplayName("a run whose observations the visibility gate withholds leaves the page entirely")
    void shouldWithholdARunMeasuredAgainstSupersededReviewRules() {
        insertFinding("Motivation is clear", "PRESENT", "GOOD", null, ArtifactKinds.PULL_REQUEST.value(), 1L);
        practice.setCriteria("Rewritten criteria, which is what makes the fingerprint differ");
        practice.setArea(area);
        practice.setCurrentRevision(practiceRevisionRepository.save(new PracticeRevision(practice, 2)));
        practiceRepository.saveAndFlush(practice);

        getHistory().jsonPath("$.content.length()").isEqualTo(0);
    }
}

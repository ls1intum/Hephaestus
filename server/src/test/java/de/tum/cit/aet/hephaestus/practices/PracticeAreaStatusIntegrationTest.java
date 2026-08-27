package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class PracticeAreaStatusIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_URI = "/workspaces/{workspaceSlug}/practice-areas/status";

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

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    private Workspace workspace;
    private PracticeArea area;
    private Practice practice;
    private AgentJob agentJob;
    private User developer;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("area-status-owner");
        workspace = createWorkspace("area-status-ws", "Area Status WS", "area-status-org", AccountType.ORG, owner);

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

    private void persistStrengthPractice(String slug, String name, long artifactId) {
        Practice target = persistPractice(workspace, area, slug, name);
        insertFinding(agentJob, target, developer, "Strength in " + name, "PRESENT", null, artifactId);
    }

    private void persistDevelopingPractice(String slug, String name, long artifactId) {
        Practice target = persistPractice(workspace, area, slug, name);
        insertFinding(agentJob, target, developer, "Gap in " + name, "ABSENT", "MAJOR", artifactId);
    }

    private void persistMixedPractice(String slug, String name, long artifactId) {
        Practice target = persistPractice(workspace, area, slug, name);
        insertFinding(agentJob, target, developer, "Strength in " + name, "PRESENT", null, artifactId);
        insertFinding(agentJob, target, developer, "Gap in " + name, "ABSENT", "MAJOR", artifactId);
    }

    private AgentJob persistAgentJob(Workspace ws) {
        AgentJob job = new AgentJob();
        job.setWorkspace(ws);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        job.setEvidenceSnapshot(OBJECT_MAPPER.valueToTree(Map.of("manifest", Map.of("contractVersion", "1.0.0"))));
        return agentJobRepository.save(job);
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        @Nullable String severity,
        Long artifactId
    ) {
        return insertFinding(job, targetPractice, user, title, presence, severity, artifactId, Instant.now());
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        @Nullable String severity,
        Long artifactId,
        Instant observedAt
    ) {
        return insertFinding(
            job,
            targetPractice,
            user,
            title,
            presence,
            severity,
            artifactId,
            observedAt,
            ArtifactKinds.PULL_REQUEST.value()
        );
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        @Nullable String severity,
        Long artifactId,
        Instant observedAt,
        String artifactKind
    ) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            job.getId(),
            targetPractice.getId(),
            targetPractice.getCurrentRevision().getId(),
            artifactKind,
            artifactId,
            user.getId(),
            title,
            presence,
            "PRESENT".equals(presence) ? "GOOD" : "BAD",
            severity,
            DIFF_EVIDENCE_JSON,
            "Test reasoning for " + title,
            null,
            observedAt,
            "LIVE"
        );
        return id;
    }

    private void insertInapplicableFinding(Practice targetPractice, String presence, Long artifactId) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            agentJob.getId(),
            targetPractice.getId(),
            targetPractice.getCurrentRevision().getId(),
            ArtifactKinds.PULL_REQUEST.value(),
            artifactId,
            developer.getId(),
            "Nothing to judge here",
            presence,
            null,
            null,
            DIFF_EVIDENCE_JSON,
            "Test reasoning for an inapplicable run",
            null,
            Instant.now(),
            "LIVE"
        );
    }

    private void deliverFeedbackFor(UUID findingId, String body) {
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(workspace.getId())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(developer.getId())
                .aboutUserId(developer.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(0)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .body(body)
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .build()
        );
        feedbackObservationRepository.insertIfAbsent(feedback.getId(), findingId, "PRIMARY", 0);
    }

    @Nested
    @DisplayName("GET /practice-areas/status")
    class DerivedStatus {

        @Test
        @WithUser
        @DisplayName("derives DEVELOPING from a confident problem and carries the delivered feedback item")
        void shouldReturnDevelopingWithEvidence() {
            UUID findingId = insertFinding(
                agentJob,
                practice,
                developer,
                "Missing rollout plan",
                "ABSENT",
                "MAJOR",
                1L
            );
            deliverFeedbackFor(findingId, "Add a rollout section describing how the change ships.");

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].areaSlug")
                .isEqualTo("code-quality")
                .jsonPath("$[0].areaName")
                .isEqualTo("Code Quality")
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].guidance")
                .isEqualTo("Your recent feedback points to “PR Description Quality” as the next practice to focus on.")
                .jsonPath("$[0].guidanceSource")
                .isEqualTo("RULE_BASED")
                .jsonPath("$[0].direction")
                .isEqualTo("INSUFFICIENT_EVIDENCE")
                .jsonPath("$[0].feedbackSpanDays")
                .isEqualTo(1)
                .jsonPath("$[0].feedbackSince")
                .exists()
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Missing rollout plan")
                .jsonPath("$[0].items[0].deliveredFeedback")
                .isEqualTo("Add a rollout section describing how the change ships.")
                .jsonPath("$[0].items[0].observationId")
                .isEqualTo(findingId.toString())
                .jsonPath("$[0].sources.length()")
                .isEqualTo(1)
                .jsonPath("$[0].sources[0].artifactKind")
                .isEqualTo(ArtifactKinds.PULL_REQUEST.value())
                .jsonPath("$[0].sources[0].count")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("counts distinct contributing artifacts per kind for the provenance line")
        void shouldCountDistinctSourceArtifactsPerKind() {
            insertFinding(agentJob, practice, developer, "Gap on PR one", "ABSENT", "MAJOR", 1L);
            insertFinding(agentJob, practice, developer, "Second gap on PR one", "ABSENT", "MINOR", 1L);
            insertFinding(agentJob, practice, developer, "Gap on PR two", "ABSENT", "MAJOR", 2L);
            insertFinding(
                agentJob,
                practice,
                developer,
                "Vague issue description",
                "ABSENT",
                "MINOR",
                7L,
                Instant.now(),
                ArtifactKinds.ISSUE.value()
            );

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].sources.length()")
                .isEqualTo(2)
                .jsonPath("$[0].sources[0].artifactKind")
                .isEqualTo(ArtifactKinds.ISSUE.value())
                .jsonPath("$[0].sources[0].count")
                .isEqualTo(1)
                .jsonPath("$[0].sources[1].artifactKind")
                .isEqualTo(ArtifactKinds.PULL_REQUEST.value())
                .jsonPath("$[0].sources[1].count")
                .isEqualTo(2);
        }

        @Test
        @WithUser
        @DisplayName("derives STRENGTH when the area only has strengths")
        void shouldReturnStrengthForGoodOnly() {
            insertFinding(agentJob, practice, developer, "Clear motivation section", "PRESENT", null, 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("STRENGTH")
                .jsonPath("$[0].guidance")
                .isEqualTo("Your recent feedback shows a strength in “PR Description Quality”. Keep building on it.")
                .jsonPath("$[0].direction")
                .isEqualTo("INSUFFICIENT_EVIDENCE")
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Clear motivation section");
        }

        @Test
        @WithUser
        @DisplayName("returns NOT_OBSERVED when the area has no observations at all")
        void shouldReturnNotObservedWithoutObservations() {
            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].areaSlug")
                .isEqualTo("code-quality")
                .jsonPath("$[0].areaName")
                .isEqualTo("Code Quality")
                .jsonPath("$[0].status")
                .isEqualTo("NOT_OBSERVED")
                .jsonPath("$[0].guidance")
                .doesNotExist()
                .jsonPath("$[0].guidanceSource")
                .doesNotExist()
                .jsonPath("$[0].direction")
                .isEqualTo("INSUFFICIENT_EVIDENCE")
                .jsonPath("$[0].feedbackSpanDays")
                .doesNotExist()
                .jsonPath("$[0].feedbackSince")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("returns NO_OPPORTUNITY when every practice ran but produced no verdict")
        void shouldReturnNoOpportunityWhenEveryRunWasInapplicable() {
            insertInapplicableFinding(practice, "NOT_APPLICABLE", 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].areaSlug")
                .isEqualTo("code-quality")
                .jsonPath("$[0].status")
                .isEqualTo("NO_OPPORTUNITY")
                .jsonPath("$[0].guidance")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("an INCONCLUSIVE run counts as an opportunity that produced no verdict")
        void shouldReturnNoOpportunityForInconclusiveRun() {
            insertInapplicableFinding(practice, "INCONCLUSIVE", 2L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("NO_OPPORTUNITY");
        }

        @Test
        @WithUser
        @DisplayName("an inapplicable run never displaces the verdict a real finding supports")
        void shouldPreferVerdictOverInapplicableRuns() {
            insertFinding(agentJob, practice, developer, "Coin-flip hunch", "ABSENT", "MINOR", 1L);
            insertInapplicableFinding(practice, "NOT_APPLICABLE", 2L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].items.length()")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("a problem seen on a single work item still yields a verdict, not an empty state")
        void shouldReportDevelopingForSingleArtifactProblem() {
            insertFinding(agentJob, practice, developer, "Coin-flip hunch", "ABSENT", "MINOR", 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].items.length()")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("derives MIXED across two practices and names both sides in the guidance")
        void shouldComposeMixedGuidanceAcrossPractices() {
            Practice reviewPractice = persistPractice(workspace, area, "review-comments", "Actionable Review Comments");
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 1L);
            insertFinding(agentJob, reviewPractice, developer, "Concrete line references", "PRESENT", null, 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED")
                .jsonPath("$[0].guidance")
                .isEqualTo(
                    "Your recent feedback shows a strength in “Actionable Review Comments”. " +
                        "Next, focus on “PR Description Quality”."
                )
                .jsonPath("$[0].items.length()")
                .isEqualTo(2)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Missing rollout plan")
                .jsonPath("$[0].items[1].title")
                .isEqualTo("Concrete line references");
        }

        @Test
        @WithUser
        @DisplayName("reads as STRENGTH when nearly every practice in the area stands as one")
        void shouldReadAsStrengthAboveTheStrengthShare() {
            persistStrengthPractice("commit-messages", "Commit Messages", 1L);
            persistStrengthPractice("review-comments", "Actionable Review Comments", 2L);
            persistStrengthPractice("issue-descriptions", "Issue Descriptions", 3L);
            persistStrengthPractice("test-coverage", "Test Coverage", 4L);
            persistMixedPractice("documentation", "Documentation", 5L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("STRENGTH");
        }

        @Test
        @WithUser
        @DisplayName("stays MIXED when the strength share only reaches the threshold")
        void shouldStayMixedAtTheStrengthShareThreshold() {
            persistStrengthPractice("commit-messages", "Commit Messages", 1L);
            persistStrengthPractice("review-comments", "Actionable Review Comments", 2L);
            persistStrengthPractice("issue-descriptions", "Issue Descriptions", 3L);
            persistStrengthPractice("test-coverage", "Test Coverage", 4L);
            persistDevelopingPractice("documentation", "Documentation", 5L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED");
        }

        @Test
        @WithUser
        @DisplayName("reads as MIXED when every practice in the area is itself mixed")
        void shouldReadAsMixedWhenEveryPracticeIsMixed() {
            persistMixedPractice("commit-messages", "Commit Messages", 1L);
            persistMixedPractice("review-comments", "Actionable Review Comments", 2L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED")
                .jsonPath("$[0].guidance")
                .value(String.class, org.hamcrest.Matchers.startsWith("Your recent feedback is mixed in "))
                .jsonPath("$[0].guidance")
                .value(String.class, org.hamcrest.Matchers.containsString("with both strengths and room to grow."));
        }

        @Test
        @WithUser
        @DisplayName("two of five standing is still a mixed area, not a developing one")
        void shouldStayMixedJustAboveTheLowerBoundary() {
            persistStrengthPractice("commit-messages", "Commit Messages", 1L);
            persistStrengthPractice("review-comments", "Actionable Review Comments", 2L);
            persistDevelopingPractice("issue-descriptions", "Issue Descriptions", 3L);
            persistDevelopingPractice("test-coverage", "Test Coverage", 4L);
            persistDevelopingPractice("documentation", "Documentation", 5L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED");
        }

        @Test
        @WithUser
        @DisplayName("reads as DEVELOPING when barely any practice in the area is standing")
        void shouldReadAsDevelopingBelowTheLowerBoundary() {
            persistStrengthPractice("commit-messages", "Commit Messages", 1L);
            persistDevelopingPractice("review-comments", "Actionable Review Comments", 2L);
            persistDevelopingPractice("issue-descriptions", "Issue Descriptions", 3L);
            persistDevelopingPractice("test-coverage", "Test Coverage", 4L);
            persistDevelopingPractice("documentation", "Documentation", 5L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING");
        }

        @Test
        @WithUser
        @DisplayName("a practice no longer reviewed does not vote in the area trend either")
        void shouldKeepAnIneligiblePracticeOutOfTheAreaTrend() {
            persistStrengthPractice("commit-messages", "Commit Messages", 1L);
            Practice retired = persistPractice(workspace, area, "test-coverage", "Test Coverage");
            retired.setAutonomy(PracticeAutonomy.OFF);
            practiceRepository.saveAndFlush(retired);
            insertFinding(agentJob, retired, developer, "Gap in Test Coverage", "ABSENT", "MAJOR", 2L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].trendSupport.currentOpportunities")
                .isEqualTo(1)
                .jsonPath("$[0].items.length()")
                .isEqualTo(2);
        }

        @Test
        @WithUser
        @DisplayName("keeps evidence from both sides when a mixed area reaches the item cap")
        void shouldKeepStrengthEvidenceWhenMixedAreaReachesItemCap() {
            Practice reviewPractice = persistPractice(workspace, area, "review-comments", "Actionable Review Comments");
            for (long artifactId = 1; artifactId <= 5; artifactId++) {
                insertFinding(agentJob, practice, developer, "Gap " + artifactId, "ABSENT", "MAJOR", artifactId);
            }
            insertFinding(agentJob, reviewPractice, developer, "Concrete line references", "PRESENT", null, 6L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED")
                .jsonPath("$[0].items.length()")
                .isEqualTo(5)
                .jsonPath("$[0].items[4].title")
                .isEqualTo("Concrete line references");
        }

        @Test
        @WithUser
        @DisplayName("a thin record still yields a verdict, but never a direction")
        void shouldNotDeriveADirectionFromTwoObservations() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Speculative gap", "ABSENT", "MINOR", 1L, previousDay);
            insertFinding(agentJob, practice, developer, "Clear motivation section", "PRESENT", null, 2L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED")
                .jsonPath("$[0].direction")
                .isEqualTo("INSUFFICIENT_EVIDENCE")
                .jsonPath("$[0].trendSupport.opportunitiesUntilComparable")
                .isEqualTo(4)
                .jsonPath("$[0].items.length()")
                .isEqualTo(2);
        }

        @Test
        @WithUser
        @DisplayName("reports IMPROVING when the latest opportunity bundle is more positive")
        void shouldReportImprovingTrajectory() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            for (long artifactId = 1; artifactId <= 4; artifactId++) {
                insertFinding(
                    agentJob,
                    practice,
                    developer,
                    "Previous gap " + artifactId,
                    "ABSENT",
                    "MAJOR",
                    artifactId,
                    previousDay
                );
            }
            for (long artifactId = 5; artifactId <= 8; artifactId++) {
                insertFinding(
                    agentJob,
                    practice,
                    developer,
                    "Current strength " + artifactId,
                    "PRESENT",
                    null,
                    artifactId
                );
            }

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("STRENGTH")
                .jsonPath("$[0].direction")
                .isEqualTo("IMPROVING")
                .jsonPath("$[0].feedbackSpanDays")
                .isEqualTo(2);
        }

        @Test
        @WithUser
        @DisplayName("reports DECLINING when the latest opportunity bundle is less positive")
        void shouldReportRegressingTrajectory() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            for (long artifactId = 1; artifactId <= 4; artifactId++) {
                insertFinding(
                    agentJob,
                    practice,
                    developer,
                    "Previous strength " + artifactId,
                    "PRESENT",
                    null,
                    artifactId,
                    previousDay
                );
            }
            for (long artifactId = 5; artifactId <= 8; artifactId++) {
                insertFinding(
                    agentJob,
                    practice,
                    developer,
                    "Current gap " + artifactId,
                    "ABSENT",
                    "MAJOR",
                    artifactId
                );
            }

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].direction")
                .isEqualTo("DECLINING");
        }

        @Test
        @WithUser
        @DisplayName("a later run that did not cover a practice does not erase what an earlier one found")
        void shouldKeepFindingsARunNeverRevisited() {
            Practice reviewPractice = persistPractice(workspace, area, "review-comments", "Actionable Review Comments");
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 1L, previousDay);
            insertFinding(
                agentJob,
                reviewPractice,
                developer,
                "Concrete line references",
                "PRESENT",
                null,
                1L,
                previousDay
            );
            AgentJob laterJob = persistAgentJob(workspace);
            insertFinding(laterJob, reviewPractice, developer, "Still concrete", "PRESENT", null, 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("MIXED")
                .jsonPath("$[0].items.length()")
                .isEqualTo(2)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Missing rollout plan");
        }

        @Test
        @WithUser
        @DisplayName("a later run that did cover the practice still supersedes what it found before")
        void shouldSupersedeFindingsTheLaterRunRevisited() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 1L, previousDay);
            AgentJob laterJob = persistAgentJob(workspace);
            insertFinding(laterJob, practice, developer, "Rollout plan added", "PRESENT", null, 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("STRENGTH")
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Rollout plan added");
        }

        @Test
        @WithUser
        @DisplayName("a later run about somebody else does not erase this developer's earlier finding")
        void shouldKeepFindingsWhenTheLaterRunWasAboutAnotherDeveloper() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 1L, previousDay);
            User otherContributor = persistUser("other-contributor");
            AgentJob laterJob = persistAgentJob(workspace);
            insertFinding(laterJob, practice, otherContributor, "Someone else's gap", "ABSENT", "MAJOR", 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("DEVELOPING")
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Missing rollout plan");
        }

        @Test
        @WithUser
        @DisplayName("does not leak another contributor's or another workspace's findings")
        void shouldNotLeakOtherContributorOrWorkspace() {
            User otherUser = persistUser("other-user");
            insertFinding(agentJob, practice, otherUser, "Someone else's gap", "ABSENT", "MAJOR", 2L);

            User otherOwner = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace(
                "other-status-ws",
                "Other WS",
                "other-status-org",
                AccountType.ORG,
                otherOwner
            );
            PracticeArea otherArea = persistArea(otherWorkspace, "code-quality", "Code Quality");
            Practice otherPractice = persistPractice(otherWorkspace, otherArea, "pr-description-quality", "PR Quality");
            AgentJob otherJob = persistAgentJob(otherWorkspace);
            insertFinding(otherJob, otherPractice, developer, "Cross-workspace gap", "ABSENT", "MAJOR", 3L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("NOT_OBSERVED")
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("returns statuses for active areas only")
        void shouldReturnStatusesForActiveAreasOnly() {
            area.setVisibleInPracticeDashboards(false);
            areaRepository.saveAndFlush(area);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
        }

        @Test
        @DisplayName("returns 401 when not logged in")
        void shouldReturnUnauthorized() {
            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }
}

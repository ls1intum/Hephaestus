package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.AreaGuidanceProvider;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional coverage for {@code GET /practice-areas/status} — the current developer's
 * derived area standing. Verifies the status derivation (problems → DEVELOPING, strengths →
 * STRENGTH, both → MIXED), the explicit NO_DATA shape, the quarantine floor, and that neither
 * another contributor's nor another workspace's findings leak into the caller's status.
 */
class PracticeAreaStatusIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_URI = "/workspaces/{workspaceSlug}/practice-areas/status";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeAreaRepository areaRepository;

    @Autowired
    private PracticeRepository practiceRepository;

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
    private User developer; // login = "testuser" to match @WithUser

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
        p.setWorkspace(ws);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Description for " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        p.setActive(true);
        p.setArea(boundArea);
        return practiceRepository.save(p);
    }

    private AgentJob persistAgentJob(Workspace ws) {
        AgentJob job = new AgentJob();
        job.setWorkspace(ws);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        return agentJobRepository.save(job);
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        String severity,
        float confidence,
        Long artifactId
    ) {
        return insertFinding(
            job,
            targetPractice,
            user,
            title,
            presence,
            severity,
            confidence,
            artifactId,
            Instant.now()
        );
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        String severity,
        float confidence,
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
            confidence,
            artifactId,
            observedAt,
            "PULL_REQUEST"
        );
    }

    private UUID insertFinding(
        AgentJob job,
        Practice targetPractice,
        User user,
        String title,
        String presence,
        String severity,
        float confidence,
        Long artifactId,
        Instant observedAt,
        String artifactType
    ) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            "key-" + id,
            job.getId(),
            targetPractice.getId(),
            null,
            artifactType,
            artifactId,
            user.getId(),
            title,
            presence,
            "PRESENT".equals(presence) ? "GOOD" : "BAD",
            severity,
            confidence,
            null,
            "Test reasoning for " + title,
            null,
            observedAt
        );
        return id;
    }

    /** Persist a DELIVERED {@link Feedback} carrying {@code body} and bind it to {@code findingId} (ADR 0021). */
    private void deliverFeedbackFor(UUID findingId, String body) {
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(workspace.getId())
                .artifactType(WorkArtifact.PULL_REQUEST)
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
                0.9f,
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
                // No provider bean registered: the deterministic sentence and its provenance marker.
                .jsonPath("$[0].guidanceSource")
                .isEqualTo("RULE_BASED")
                // One evidence-bearing day has no previous daily snapshot to compare with.
                .jsonPath("$[0].trajectory")
                .doesNotExist()
                // Observed just now: the verdict rests on a single day of feedback.
                .jsonPath("$[0].feedbackSpanDays")
                .isEqualTo(1)
                .jsonPath("$[0].feedbackSince")
                .exists()
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Missing rollout plan")
                .jsonPath("$[0].items[0].guidance")
                .isEqualTo("Add a rollout section describing how the change ships.")
                .jsonPath("$[0].items[0].observationId")
                .isEqualTo(findingId.toString())
                // Provenance: the verdict rests on exactly one pull request.
                .jsonPath("$[0].sources.length()")
                .isEqualTo(1)
                .jsonPath("$[0].sources[0].source")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$[0].sources[0].count")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("counts distinct contributing artifacts per kind for the provenance line")
        void shouldCountDistinctSourceArtifactsPerKind() {
            // Two observations on PR 1 must count that PR once; PR 2 and issue 7 add one each of their kind.
            insertFinding(agentJob, practice, developer, "Gap on PR one", "ABSENT", "MAJOR", 0.9f, 1L);
            insertFinding(agentJob, practice, developer, "Second gap on PR one", "ABSENT", "MINOR", 0.9f, 1L);
            insertFinding(agentJob, practice, developer, "Gap on PR two", "ABSENT", "MAJOR", 0.9f, 2L);
            insertFinding(
                agentJob,
                practice,
                developer,
                "Vague issue description",
                "ABSENT",
                "MINOR",
                0.9f,
                7L,
                Instant.now(),
                "ISSUE"
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
                // WorkArtifact declaration order keeps the rendering stable: pull requests first.
                .jsonPath("$[0].sources[0].source")
                .isEqualTo("PULL_REQUEST")
                .jsonPath("$[0].sources[0].count")
                .isEqualTo(2)
                .jsonPath("$[0].sources[1].source")
                .isEqualTo("ISSUE")
                .jsonPath("$[0].sources[1].count")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("derives STRENGTH when the area only has strengths")
        void shouldReturnStrengthForGoodOnly() {
            insertFinding(agentJob, practice, developer, "Clear motivation section", "PRESENT", null, 0.9f, 1L);

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
                // One evidence-bearing day has no previous daily snapshot to compare with.
                .jsonPath("$[0].trajectory")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(1)
                .jsonPath("$[0].items[0].title")
                .isEqualTo("Clear motivation section");
        }

        @Test
        @WithUser
        @DisplayName("returns NO_DATA when the area has no observations at all")
        void shouldReturnNoDataWithoutObservations() {
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
                .isEqualTo("NO_DATA")
                .jsonPath("$[0].guidance")
                .doesNotExist()
                .jsonPath("$[0].guidanceSource")
                .doesNotExist()
                .jsonPath("$[0].trajectory")
                .doesNotExist()
                .jsonPath("$[0].feedbackSpanDays")
                .doesNotExist()
                .jsonPath("$[0].feedbackSince")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("quarantines a low-confidence single-target problem into NO_DATA")
        void shouldQuarantineLowConfidenceSingleTargetProblem() {
            // Below the 0.5 quarantine floor and seen on ONE artifact only: must never reach the
            // learner surface, so the area reads as NO_DATA rather than DEVELOPING.
            insertFinding(agentJob, practice, developer, "Coin-flip hunch", "ABSENT", "MINOR", 0.3f, 1L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("NO_DATA")
                // A quarantined-only area must not pair "nothing to show" with a direction.
                .jsonPath("$[0].trajectory")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("derives MIXED across two practices and names both sides in the guidance")
        void shouldComposeMixedGuidanceAcrossPractices() {
            Practice reviewPractice = persistPractice(workspace, area, "review-comments", "Actionable Review Comments");
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 0.9f, 1L);
            insertFinding(agentJob, reviewPractice, developer, "Concrete line references", "PRESENT", null, 0.9f, 1L);

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
        @DisplayName("keeps evidence from both sides when a mixed area reaches the item cap")
        void shouldKeepStrengthEvidenceWhenMixedAreaReachesItemCap() {
            Practice reviewPractice = persistPractice(workspace, area, "review-comments", "Actionable Review Comments");
            for (long artifactId = 1; artifactId <= 5; artifactId++) {
                insertFinding(agentJob, practice, developer, "Gap " + artifactId, "ABSENT", "MAJOR", 0.9f, artifactId);
            }
            insertFinding(agentJob, reviewPractice, developer, "Concrete line references", "PRESENT", null, 0.9f, 6L);

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
        @DisplayName("does not derive a negative trajectory from a quarantined problem beside a strength")
        void shouldNotContradictStrengthWithQuarantinedProblemTrajectory() {
            insertFinding(agentJob, practice, developer, "Clear motivation section", "PRESENT", null, 0.9f, 1L);
            insertFinding(agentJob, practice, developer, "Speculative gap", "ABSENT", "MINOR", 0.3f, 2L);

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
                .jsonPath("$[0].trajectory")
                .doesNotExist()
                .jsonPath("$[0].items.length()")
                .isEqualTo(1);
        }

        @Test
        @WithUser
        @DisplayName("reports IMPROVING when a practice's latest evidence day is more positive")
        void shouldReportImprovingTrajectory() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Previous gap", "ABSENT", "MAJOR", 0.9f, 1L, previousDay);
            insertFinding(agentJob, practice, developer, "Current strength", "PRESENT", null, 0.9f, 2L);

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
                .jsonPath("$[0].trajectory")
                .isEqualTo("IMPROVING")
                // The two daily snapshots span yesterday and today.
                .jsonPath("$[0].feedbackSpanDays")
                .isEqualTo(2);
        }

        @Test
        @WithUser
        @DisplayName("reports REGRESSING when a practice's latest evidence day is less positive")
        void shouldReportRegressingTrajectory() {
            Instant previousDay = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
            insertFinding(agentJob, practice, developer, "Previous strength", "PRESENT", null, 0.9f, 1L, previousDay);
            insertFinding(agentJob, practice, developer, "Current gap", "ABSENT", "MAJOR", 0.9f, 2L);

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
                .jsonPath("$[0].trajectory")
                .isEqualTo("REGRESSING");
        }

        @Test
        @WithUser
        @DisplayName("does not leak another contributor's or another workspace's findings")
        void shouldNotLeakOtherContributorOrWorkspace() {
            // Another contributor's finding in THIS workspace and area.
            User otherUser = persistUser("other-user");
            insertFinding(agentJob, practice, otherUser, "Someone else's gap", "ABSENT", "MAJOR", 0.9f, 2L);

            // The developer's OWN finding, but in a same-slug area of a DIFFERENT workspace.
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
            insertFinding(otherJob, otherPractice, developer, "Cross-workspace gap", "ABSENT", "MAJOR", 0.9f, 3L);

            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("NO_DATA")
                .jsonPath("$[0].items.length()")
                .isEqualTo(0);
        }

        @Test
        @WithUser
        @DisplayName("returns statuses for active areas only")
        void shouldReturnStatusesForActiveAreasOnly() {
            area.setActive(false);
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

    /**
     * The guidance seam: a registered {@link AreaGuidanceProvider} bean (later: the persisted nightly
     * LLM aggregation) replaces the deterministic sentence, labelled with its provenance — but never
     * conjures guidance for an area whose status is NO_DATA.
     */
    @Nested
    @DisplayName("with an aggregated guidance provider registered")
    @Import(PracticeAreaStatusIntegrationTest.AggregatedGuidanceConfig.class)
    class AggregatedGuidance {

        private static final String AI_GUIDANCE =
            "Your PR descriptions consistently explain the what — add a sentence on the why to make them review-ready.";

        @Test
        @WithUser
        @DisplayName("serves the provider's text with AI_AGGREGATED provenance instead of the rule-based sentence")
        void shouldPreferAggregatedGuidance() {
            insertFinding(agentJob, practice, developer, "Missing rollout plan", "ABSENT", "MAJOR", 0.9f, 1L);

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
                .jsonPath("$[0].guidance")
                .isEqualTo(AI_GUIDANCE)
                .jsonPath("$[0].guidanceSource")
                .isEqualTo("AI_AGGREGATED");
        }

        @Test
        @WithUser
        @DisplayName("suppresses provider guidance for a NO_DATA area")
        void shouldNotShowAggregatedGuidanceWithoutData() {
            webTestClient
                .get()
                .uri(STATUS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("NO_DATA")
                .jsonPath("$[0].guidance")
                .doesNotExist()
                .jsonPath("$[0].guidanceSource")
                .doesNotExist();
        }
    }

    @TestConfiguration
    static class AggregatedGuidanceConfig {

        @Bean
        AreaGuidanceProvider aggregatedAreaGuidanceProvider() {
            return (workspaceId, userId, areaSlugs) ->
                areaSlugs.contains("code-quality")
                    ? Map.of(
                          "code-quality",
                          new AreaGuidanceProvider.AreaGuidance(
                              AggregatedGuidance.AI_GUIDANCE,
                              PracticeAreaStatusDTO.GuidanceSource.AI_AGGREGATED
                          )
                      )
                    : Map.of();
        }
    }
}

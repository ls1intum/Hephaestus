package de.tum.in.www1.hephaestus.practices.finding.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.CreateFindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackEngagementDTO;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Finding feedback controller integration")
class FindingFeedbackControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FEEDBACK_URI = "/workspaces/{workspaceSlug}/practices/findings/{findingId}/feedback";
    private static final String ENGAGEMENT_URI = "/workspaces/{workspaceSlug}/practices/findings/engagement";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

    @Autowired
    private FindingFeedbackRepository feedbackRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace workspace;
    private User adminUser;
    private PracticeFinding finding;

    @BeforeEach
    void setUpTestData() {
        // Create workspace with an owner
        User owner = persistUser("feedback-owner");
        workspace = createWorkspace("feedback-ws", "Feedback WS", "feedback-org", AccountType.ORG, owner);

        // Ensure the "admin" user (from @WithAdminUser) exists and has workspace membership
        adminUser = ensureAdminMembership(workspace).getUser();

        // Create practice
        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCategory("test");
        practice.setDescription("Test description");
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);

        // Create agent job (required FK for practice_finding)
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        // Create a practice finding with the admin user as contributor
        finding = PracticeFinding.builder()
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .agentJobId(agentJob.getId())
            .practice(practice)
            .targetType(PracticeFindingTargetType.PULL_REQUEST)
            .targetId(42L)
            .contributor(adminUser)
            .title("Missing error handling")
            .verdict(Verdict.NEGATIVE)
            .severity(Severity.MAJOR)
            .confidence(0.85f)
            .detectedAt(Instant.now())
            .build();
        practiceFindingRepository.save(finding);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /{findingId}/feedback
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /{findingId}/feedback")
    class SubmitFeedback {

        @Test
        @WithAdminUser
        @DisplayName("APPLIED feedback returns 201")
        void appliedReturns201() {
            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);

            FindingFeedbackDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(FindingFeedbackDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingFeedbackAction.APPLIED);
            assertThat(response.findingId()).isEqualTo(finding.getId());
            assertThat(response.id()).isNotNull();
            assertThat(response.createdAt()).isNotNull();
        }

        @Test
        @WithAdminUser
        @DisplayName("DISPUTED with explanation returns 201")
        void disputedWithExplanationReturns201() {
            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "The AI is wrong about this");

            FindingFeedbackDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(FindingFeedbackDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingFeedbackAction.DISPUTED);
            assertThat(response.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        @WithAdminUser
        @DisplayName("DISPUTED without explanation returns 400")
        void disputedWithoutExplanationReturns400() {
            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        @DisplayName("append-only: second feedback creates new row")
        void appendOnlyCreatesNewRow() {
            var request1 = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            var request2 = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "Changed my mind");

            // First feedback
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isCreated();

            // Second feedback — should create new row, not upsert
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isCreated();

            // Verify two rows exist
            assertThat(feedbackRepository.findAll()).hasSize(2);
        }

        @Test
        @WithAdminUser
        @DisplayName("non-existent finding returns 404")
        void nonExistentFindingReturns404() {
            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), UUID.randomUUID())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithMentorUser
        @DisplayName("non-contributor returns 403")
        void nonContributorReturns403() {
            // "mentor" user exists in DB and has workspace membership, but is NOT the finding's contributor
            User mentorUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, mentorUser, WorkspaceMembership.WorkspaceRole.MEMBER);

            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void unauthenticatedReturns401() {
            var request = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /{findingId}/feedback
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /{findingId}/feedback")
    class GetLatestFeedback {

        @Test
        @WithAdminUser
        @DisplayName("returns 204 when no feedback exists")
        void returns204WhenEmpty() {
            webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns 404 for non-existent finding")
        void returns404ForNonExistentFinding() {
            webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), UUID.randomUUID())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns latest feedback after multiple submissions")
        void returnsLatestAfterMultipleSubmissions() {
            // Submit two feedbacks
            var request1 = new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null);
            var request2 = new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "Actually wrong");

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isCreated();

            // GET should return the latest (DISPUTED)
            FindingFeedbackDTO response = webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingFeedbackDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingFeedbackAction.DISPUTED);
            assertThat(response.explanation()).isEqualTo("Actually wrong");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Workspace isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Workspace isolation")
    class WorkspaceIsolation {

        @Test
        @WithAdminUser
        @DisplayName("engagement does not count feedback from other workspaces")
        void engagementIsScopedToWorkspace() {
            // Submit feedback in the main workspace
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            // Create a second workspace and add the admin user
            User owner2 = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace("other-ws", "Other WS", "other-org", AccountType.ORG, owner2);
            ensureWorkspaceMembership(otherWorkspace, adminUser, WorkspaceMembership.WorkspaceRole.ADMIN);

            // Engagement in the second workspace should be all zeros
            FindingFeedbackEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, otherWorkspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingFeedbackEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.applied()).isZero();
            assertThat(response.disputed()).isZero();
            assertThat(response.notApplicable()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /engagement
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /engagement")
    class GetEngagement {

        @Test
        @WithAdminUser
        @DisplayName("returns all zeros when no feedback exists")
        void returnsZerosWhenNoFeedback() {
            FindingFeedbackEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingFeedbackEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.applied()).isZero();
            assertThat(response.disputed()).isZero();
            assertThat(response.notApplicable()).isZero();
        }

        @Test
        @WithAdminUser
        @DisplayName("returns correct action counts")
        void returnsCorrectCounts() {
            // Submit multiple feedbacks
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingFeedbackDTO(FindingFeedbackAction.APPLIED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), finding.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingFeedbackDTO(FindingFeedbackAction.DISPUTED, "Wrong detection"))
                .exchange()
                .expectStatus()
                .isCreated();

            FindingFeedbackEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingFeedbackEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.applied()).isEqualTo(1);
            assertThat(response.disputed()).isEqualTo(1);
            assertThat(response.notApplicable()).isZero();
        }
    }
}

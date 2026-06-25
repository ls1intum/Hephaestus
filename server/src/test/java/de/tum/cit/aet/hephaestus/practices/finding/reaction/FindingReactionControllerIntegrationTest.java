package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackProvenance;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSurface;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.CreateFindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class FindingReactionControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FEEDBACK_URI = "/workspaces/{workspaceSlug}/practices/feedback/{feedbackId}/reactions";
    private static final String ENGAGEMENT_URI = "/workspaces/{workspaceSlug}/practices/feedback/engagement";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FindingReactionRepository reactionRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace workspace;
    private User adminUser;
    private Feedback feedbackUnit;

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
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);

        // Create agent job (required FK for practice_finding)
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        // Create a practice finding with the admin user as subject
        Observation finding = Observation.builder()
            .occurrenceKey("test-key-" + UUID.randomUUID())
            .agentJobId(agentJob.getId())
            .practice(practice)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(42L)
            .aboutUserId(adminUser.getId())
            .title("Missing error handling")
            .presence(Presence.ABSENT)
            .assessment(Assessment.BAD)
            .severity(Severity.MAJOR)
            .confidence(0.85f)
            .observedAt(Instant.now())
            .build();
        practiceFindingRepository.save(finding);

        // Create the delivered feedback unit the admin user reacts to (recipient == subject).
        feedbackUnit = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(workspace.getId())
                .artifactType(WorkArtifact.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(adminUser.getId())
                .subjectUserId(adminUser.getId())
                .channel(FeedbackSurface.IN_CONTEXT)
                .position(0)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .source(FeedbackProvenance.AGENT)
                .createdAt(Instant.now())
                .deliveredAt(Instant.now())
                .build()
        );
    }

    // POST /{feedbackId}/reactions

    @Nested
    @DisplayName("POST /{feedbackId}/reactions")
    class SubmitFeedback {

        @Test
        @WithAdminUser
        void appliedReturns201() {
            var request = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);

            FindingReactionDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(FindingReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingReactionAction.ADDRESSED);
            assertThat(response.feedbackId()).isEqualTo(feedbackUnit.getId());
            assertThat(response.id()).isNotNull();
            assertThat(response.createdAt()).isNotNull();
        }

        @Test
        @WithAdminUser
        void disputedWithExplanationReturns201() {
            var request = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "The AI is wrong about this");

            FindingReactionDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(FindingReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingReactionAction.DISPUTED);
            assertThat(response.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        @WithAdminUser
        void disputedWithoutExplanationReturns400() {
            var request = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
        }

        @Test
        @WithAdminUser
        void appendOnlyCreatesNewRow() {
            var request1 = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);
            var request2 = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "Changed my mind");

            // First feedback
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isCreated();

            // Second feedback — should create new row, not upsert
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isCreated();

            // Verify two rows exist
            assertThat(reactionRepository.findAll()).hasSize(2);
        }

        @Test
        @WithAdminUser
        void nonExistentFeedbackReturns404() {
            var request = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);

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
        void nonRecipientReturns403() {
            // "mentor" user exists in DB and has workspace membership, but is NOT the feedback's recipient
            User mentorUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, mentorUser, WorkspaceMembership.WorkspaceRole.MEMBER);

            var request = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        void unauthenticatedMutationRejected() {
            var request = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);

            // Anonymous POST → the double-submit CSRF gate (ADR 0017) rejects it 403 before auth runs
            // (no X-XSRF-TOKEN). The mutation stays blocked for anonymous callers.
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    // GET /{feedbackId}/reactions

    @Nested
    class GetLatestFeedback {

        @Test
        @WithAdminUser
        @DisplayName("returns 204 when no feedback exists")
        void returns204WhenEmpty() {
            webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent();
        }

        @Test
        @WithAdminUser
        void returns404ForNonExistentFeedback() {
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
        void returnsLatestAfterMultipleSubmissions() {
            // Submit two feedbacks
            var request1 = new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null);
            var request2 = new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "Actually wrong");

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isCreated();

            // GET should return the latest (DISPUTED)
            FindingReactionDTO response = webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(FindingReactionAction.DISPUTED);
            assertThat(response.explanation()).isEqualTo("Actually wrong");
        }
    }

    // Workspace isolation

    @Nested
    class WorkspaceIsolation {

        @Test
        @WithAdminUser
        @DisplayName("engagement does not count feedback from other workspaces")
        void engagementIsScopedToWorkspace() {
            // Submit feedback in the main workspace
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            // Create a second workspace and add the admin user
            User owner2 = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace("other-ws", "Other WS", "other-org", AccountType.ORG, owner2);
            ensureWorkspaceMembership(otherWorkspace, adminUser, WorkspaceMembership.WorkspaceRole.ADMIN);

            // Engagement in the second workspace should be all zeros
            FindingReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, otherWorkspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingReactionEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.addressed()).isZero();
            assertThat(response.disputed()).isZero();
            assertThat(response.notApplicable()).isZero();
        }
    }

    // GET /engagement

    @Nested
    class GetEngagement {

        @Test
        @WithAdminUser
        void returnsZerosWhenNoFeedback() {
            FindingReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingReactionEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.addressed()).isZero();
            assertThat(response.disputed()).isZero();
            assertThat(response.notApplicable()).isZero();
        }

        @Test
        @WithAdminUser
        void returnsCorrectCounts() {
            // Submit multiple feedbacks
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingReactionDTO(FindingReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateFindingReactionDTO(FindingReactionAction.DISPUTED, "Wrong detection"))
                .exchange()
                .expectStatus()
                .isCreated();

            FindingReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(FindingReactionEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.addressed()).isEqualTo(1);
            assertThat(response.disputed()).isEqualTo(1);
            assertThat(response.notApplicable()).isZero();
        }
    }
}

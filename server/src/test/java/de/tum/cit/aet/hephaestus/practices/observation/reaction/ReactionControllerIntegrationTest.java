package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.CreateReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionEngagementDTO;
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

class ReactionControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FEEDBACK_URI = "/workspaces/{workspaceSlug}/practices/feedback/{feedbackId}/reactions";
    private static final String ENGAGEMENT_URI = "/workspaces/{workspaceSlug}/practices/feedback/engagement";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private static final String HEADLINE_RECURRENCE_KEY = "ck-integration-headline";

    private Workspace workspace;
    private User adminUser;
    private Feedback feedbackUnit;

    @BeforeEach
    void setUpTestData() {
        User owner = persistUser("feedback-owner");
        workspace = createWorkspace("feedback-ws", "Feedback WS", "feedback-org", AccountType.ORG, owner);

        // The "admin" user (from @WithAdminUser) must exist and hold workspace membership.
        adminUser = ensureAdminMembership(workspace).getUser();

        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);

        // Agent job is a required FK for the observation.
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

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
            .recurrenceKey(HEADLINE_RECURRENCE_KEY)
            .observedAt(Instant.now())
            .build();
        finding = observationRepository.save(finding);

        // Create the delivered feedback unit the admin user reacts to (recipient == subject).
        feedbackUnit = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(workspace.getId())
                .artifactType(WorkArtifact.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(adminUser.getId())
                .aboutUserId(adminUser.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(0)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .deliveredAt(Instant.now())
                .build()
        );

        // Bind the observation as the feedback's PRIMARY evidence so findHeadlineRecurrenceKey resolves the
        // headline locus the reaction must denormalize (B2).
        feedbackObservationRepository.insertIfAbsent(
            feedbackUnit.getId(),
            finding.getId(),
            de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY.name(),
            0
        );
    }

    // POST /{feedbackId}/reactions

    @Nested
    @DisplayName("POST /{feedbackId}/reactions")
    class SubmitFeedback {

        @Test
        @WithAdminUser
        void appliedReturns201() {
            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);

            ReactionDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(ReactionAction.ADDRESSED);
            assertThat(response.feedbackId()).isEqualTo(feedbackUnit.getId());
            assertThat(response.id()).isNotNull();
            assertThat(response.createdAt()).isNotNull();

            // B2 denormalization: the persisted reaction carries the feedback's headline recurrence key.
            Reaction saved = reactionRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getRecurrenceKey()).isEqualTo(HEADLINE_RECURRENCE_KEY);
        }

        @Test
        @WithAdminUser
        void disputedWithExplanationReturns201() {
            var request = new CreateReactionDTO(ReactionAction.DISPUTED, "The AI is wrong about this");

            ReactionDTO response = webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(ReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(ReactionAction.DISPUTED);
            assertThat(response.explanation()).isEqualTo("The AI is wrong about this");
        }

        @Test
        @WithAdminUser
        void disputedWithoutExplanationReturns400() {
            var request = new CreateReactionDTO(ReactionAction.DISPUTED, null);

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
            var request1 = new CreateReactionDTO(ReactionAction.ADDRESSED, null);
            var request2 = new CreateReactionDTO(ReactionAction.DISPUTED, "Changed my mind");

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isCreated();

            // Append-only: the second submit creates a new row rather than upserting.
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isCreated();

            assertThat(reactionRepository.findAll()).hasSize(2);
        }

        @Test
        @WithAdminUser
        void nonExistentFeedbackReturns404() {
            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);

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

            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);

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
        @WithMentorUser
        void subjectButNotRecipientReturns403() {
            // Reviewer-side firewall: the gate is the RECIPIENT (who was delivered to), not the SUBJECT
            // (who the feedback is ABOUT). Build a unit delivered to the admin but ABOUT the mentor, then
            // authenticate as the mentor (the subject, a workspace member) — the subject must NOT be able
            // to react to feedback they were never shown.
            User mentorUser = persistUser("mentor");
            ensureWorkspaceMembership(workspace, mentorUser, WorkspaceMembership.WorkspaceRole.MEMBER);

            Feedback aboutMentorDeliveredToAdmin = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(feedbackUnit.getAgentJobId())
                    .workspaceId(workspace.getId())
                    .artifactType(WorkArtifact.PULL_REQUEST)
                    .artifactId(43L)
                    .recipientUserId(adminUser.getId())
                    .aboutUserId(mentorUser.getId())
                    .channel(FeedbackChannel.IN_CONTEXT)
                    // position 1: distinct unit within the same job (uk_feedback_unit is (agent_job_id, position)).
                    .position(1)
                    .deliveryState(FeedbackDeliveryState.DELIVERED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(Instant.now())
                    .build()
            );

            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), aboutMentorDeliveredToAdmin.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        void unauthenticatedMutationRejected() {
            var request = new CreateReactionDTO(ReactionAction.ADDRESSED, null);

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
        @DisplayName("returns 204 when no reaction has been submitted yet")
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
            var request1 = new CreateReactionDTO(ReactionAction.ADDRESSED, null);
            var request2 = new CreateReactionDTO(ReactionAction.DISPUTED, "Actually wrong");

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

            ReactionDTO response = webTestClient
                .get()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ReactionDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.action()).isEqualTo(ReactionAction.DISPUTED);
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
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateReactionDTO(ReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            User owner2 = persistUser("other-ws-owner");
            Workspace otherWorkspace = createWorkspace("other-ws", "Other WS", "other-org", AccountType.ORG, owner2);
            ensureWorkspaceMembership(otherWorkspace, adminUser, WorkspaceMembership.WorkspaceRole.ADMIN);

            // Engagement in the second workspace must be all zeros — reactions do not cross workspaces.
            ReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, otherWorkspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ReactionEngagementDTO.class)
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
            ReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ReactionEngagementDTO.class)
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
            // C1: react ADDRESSED then change the mind to DISPUTED on the SAME feedback unit. Reaction is
            // @Immutable / append-only, so the second submit appends a new row and only the LATEST row per
            // feedback_id is the current reaction. Engagement must therefore count ONLY the latest (DISPUTED=1),
            // not double-count the superseded ADDRESSED row.
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateReactionDTO(ReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateReactionDTO(ReactionAction.DISPUTED, "Wrong detection"))
                .exchange()
                .expectStatus()
                .isCreated();

            ReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ReactionEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            // The superseded ADDRESSED row no longer counts — only the current DISPUTED reaction does.
            assertThat(response.addressed()).isZero();
            assertThat(response.disputed()).isEqualTo(1);
            assertThat(response.notApplicable()).isZero();
        }

        @Test
        @WithAdminUser
        void countsDistinctFeedbackUnitsNotHistoricalRows() {
            // Two DISTINCT feedback units, each reacted ADDRESSED once → addressed=2 (distinct current
            // reactions). Guards that the DISTINCT ON (feedback_id) collapse does not over-collapse across
            // different feedback units.
            Feedback secondFeedbackUnit = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(feedbackUnit.getAgentJobId())
                    .workspaceId(workspace.getId())
                    .artifactType(WorkArtifact.PULL_REQUEST)
                    .artifactId(42L)
                    .recipientUserId(feedbackUnit.getRecipientUserId())
                    .aboutUserId(feedbackUnit.getAboutUserId())
                    .channel(FeedbackChannel.IN_CONTEXT)
                    .position(1)
                    .deliveryState(FeedbackDeliveryState.DELIVERED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .deliveredAt(Instant.now())
                    .build()
            );

            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), feedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateReactionDTO(ReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();
            webTestClient
                .post()
                .uri(FEEDBACK_URI, workspace.getWorkspaceSlug(), secondFeedbackUnit.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateReactionDTO(ReactionAction.ADDRESSED, null))
                .exchange()
                .expectStatus()
                .isCreated();

            ReactionEngagementDTO response = webTestClient
                .get()
                .uri(ENGAGEMENT_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ReactionEngagementDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.addressed()).isEqualTo(2);
        }
    }
}

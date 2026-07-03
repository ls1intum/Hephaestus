package de.tum.cit.aet.hephaestus.integration.slack.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEventRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end tests for {@link SlackChannelAdminController} — the per-workspace channel activation control plane. The
 * whole Slack surface is enabled ({@code hephaestus.integration.slack.enabled=true}) so the guarded controller +
 * service + audit repository run against a real Postgres via {@link WebTestClient}. {@link SlackMessageService} is
 * mocked so the activation announcement side effect is observable without a live Slack workspace.
 *
 * <p>Each test asserts a real behavior that would fail if the guard or side effect were removed: activation posts the
 * announcement + stamps the forward-only boundary + writes an audit row; a non-admin is 403; an illegal edge is 409;
 * revoke erases raw + derived data; the audit trail lists chronologically; and another workspace's channel is 404.
 */
@TestPropertySource(properties = "hephaestus.integration.slack.enabled=true")
class SlackChannelAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Autowired
    private SlackChannelConsentEventRepository consentEventRepository;

    @Autowired
    private SlackMessageRepository messageRepository;

    @Autowired
    private SlackThreadRepository threadRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @MockitoBean
    private SlackMessageService slackMessageService;

    private Workspace workspace;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("slack-chan-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "slack-chan-" + System.nanoTime(),
            "Slack Channel Test",
            "slack-chan-org",
            AccountType.ORG,
            owner
        );
    }

    @Test
    @WithAdminUser
    @DisplayName("admin activates PENDING → ACTIVE: announcement posted, announced_at stamped, audit row written")
    void activatePendingChannel_asAdmin() {
        ensureAdminMembership(workspace);
        seedChannel(workspace.getId(), "C1", ConsentState.PENDING, null);

        SlackMonitoredChannelDTO result = patchConsent("C1", ConsentState.ACTIVE, "pilot go")
            .expectStatus()
            .isOk()
            .expectBody(SlackMonitoredChannelDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.consentState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(result.consentAnnouncedAt()).isNotNull();

        // Announcement side effect fired for this channel.
        verify(slackMessageService).sendForWorkspace(eq(workspace.getId()), eq("C1"), anyList(), any());

        // Persisted state: ACTIVE + stamped.
        SlackMonitoredChannel stored = monitoredChannelRepository
            .findByWorkspaceIdAndSlackChannelId(workspace.getId(), "C1")
            .orElseThrow();
        assertThat(stored.getConsentState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(stored.getConsentAnnouncedAt()).isNotNull();

        // Audit row: PENDING → ACTIVE by the admin.
        var events = consentEventRepository.findByWorkspaceIdAndSlackChannelIdOrderByCreatedAtAscIdAsc(
            workspace.getId(),
            "C1"
        );
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromState()).isEqualTo(ConsentState.PENDING);
        assertThat(events.get(0).getToState()).isEqualTo(ConsentState.ACTIVE);
        assertThat(events.get(0).getActorUserId()).isEqualTo(adminUserId());
        assertThat(events.get(0).getReason()).isEqualTo("pilot go");
    }

    @Test
    @WithMentorUser
    @DisplayName("non-admin cannot activate a channel → 403, state unchanged")
    void activate_asNonAdmin_forbidden() {
        persistUser("mentor");
        User member = persistUser("member-" + System.nanoTime());
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);
        seedChannel(workspace.getId(), "C1", ConsentState.PENDING, null);

        patchConsent("C1", ConsentState.ACTIVE, null).expectStatus().isForbidden();

        assertThat(
            monitoredChannelRepository
                .findByWorkspaceIdAndSlackChannelId(workspace.getId(), "C1")
                .orElseThrow()
                .getConsentState()
        ).isEqualTo(ConsentState.PENDING);
        verify(slackMessageService, never()).sendForWorkspace(any(Long.class), any(), anyList(), any());
    }

    @Test
    @WithAdminUser
    @DisplayName("illegal transition PENDING → PAUSED → 409, no audit row")
    void illegalTransition_conflict() {
        ensureAdminMembership(workspace);
        seedChannel(workspace.getId(), "C1", ConsentState.PENDING, null);

        patchConsent("C1", ConsentState.PAUSED, null).expectStatus().isEqualTo(409);

        assertThat(
            consentEventRepository.findByWorkspaceIdAndSlackChannelIdOrderByCreatedAtAscIdAsc(workspace.getId(), "C1")
        ).isEmpty();
    }

    @Test
    @WithAdminUser
    @DisplayName("pause then resume: ACTIVE → PAUSED → ACTIVE; resume does not re-announce")
    void pauseThenResume() {
        ensureAdminMembership(workspace);
        seedChannel(workspace.getId(), "C1", ConsentState.ACTIVE, Instant.parse("2020-01-01T00:00:00Z"));

        patchConsent("C1", ConsentState.PAUSED, null).expectStatus().isOk();
        assertThat(currentState("C1")).isEqualTo(ConsentState.PAUSED);

        patchConsent("C1", ConsentState.ACTIVE, null).expectStatus().isOk();
        assertThat(currentState("C1")).isEqualTo(ConsentState.ACTIVE);

        // The channel was already announced at seed time → resume must not post another notice.
        verify(slackMessageService, never()).sendForWorkspace(any(Long.class), any(), anyList(), any());

        var events = consentEventRepository.findByWorkspaceIdAndSlackChannelIdOrderByCreatedAtAscIdAsc(
            workspace.getId(),
            "C1"
        );
        assertThat(events)
            .extracting(e -> e.getToState())
            .containsExactly(ConsentState.PAUSED, ConsentState.ACTIVE);
    }

    @Test
    @WithAdminUser
    @DisplayName("DELETE revokes + erases the channel's raw messages, thread aggregate, and derived feedback")
    void revokeErasesRawAndDerived() {
        ensureAdminMembership(workspace);
        seedChannel(workspace.getId(), "C1", ConsentState.ACTIVE, Instant.parse("2020-01-01T00:00:00Z"));

        // Raw content: one message + one thread aggregate.
        messageRepository.insertIfAbsent(workspace.getId(), "T1", "C1", "100.1", null, "U1", null, "hello");
        long threadId = seedThread(workspace.getId(), "C1", "100.1");
        // Derived CONVERSATION_THREAD observation + feedback anchored to the thread id.
        UUID observationId = seedBoundConversation(threadId);
        UUID feedbackId = lastFeedbackId;

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/slack/channels/{c}", workspace.getWorkspaceSlug(), "C1")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        // Channel flipped to REVOKED (row survives as the terminal record) …
        assertThat(currentState("C1")).isEqualTo(ConsentState.REVOKED);
        // … raw content gone …
        assertThat(
            messageRepository.existsByWorkspaceIdAndSlackChannelIdAndSlackTs(workspace.getId(), "C1", "100.1")
        ).isFalse();
        assertThat(threadRepository.findById(threadId)).isEmpty();
        // … derived feedback + observation gone.
        assertThat(feedbackRepository.findById(feedbackId)).isEmpty();
        assertThat(observationRepository.findById(observationId)).isEmpty();
    }

    @Test
    @WithAdminUser
    @DisplayName("consent-events lists the channel's transition history chronologically")
    void auditListReturnsHistory() {
        ensureAdminMembership(workspace);
        seedChannel(workspace.getId(), "C1", ConsentState.PENDING, null);

        patchConsent("C1", ConsentState.ACTIVE, "go").expectStatus().isOk();
        patchConsent("C1", ConsentState.PAUSED, "pause for review").expectStatus().isOk();

        List<SlackChannelConsentEventDTO> events = webTestClient
            .get()
            .uri("/workspaces/{slug}/slack/channels/{c}/consent-events", workspace.getWorkspaceSlug(), "C1")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(SlackChannelConsentEventDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(events).hasSize(2);
        assertThat(events)
            .extracting(SlackChannelConsentEventDTO::toState)
            .containsExactly(ConsentState.ACTIVE, ConsentState.PAUSED);
        assertThat(events.get(0).actorUserId()).isEqualTo(adminUserId());
    }

    @Test
    @WithAdminUser
    @DisplayName("another workspace's channel is not visible → 404 (isolation)")
    void workspaceIsolation() {
        ensureAdminMembership(workspace);
        Workspace other = createWorkspace(
            "slack-other-" + System.nanoTime(),
            "Other",
            "other-org",
            AccountType.ORG,
            persistUser("other-owner-" + System.nanoTime())
        );
        seedChannel(other.getId(), "C-OTHER", ConsentState.PENDING, null);

        // Reaching the other workspace's channel through THIS workspace's URL resolves to nothing → 404.
        patchConsent("C-OTHER", ConsentState.ACTIVE, null).expectStatus().isNotFound();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/slack/channels/{c}/consent-events", workspace.getWorkspaceSlug(), "C-OTHER")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();

        // The other workspace's channel is untouched.
        assertThat(
            monitoredChannelRepository
                .findByWorkspaceIdAndSlackChannelId(other.getId(), "C-OTHER")
                .orElseThrow()
                .getConsentState()
        ).isEqualTo(ConsentState.PENDING);
    }

    // --- helpers ---

    private WebTestClient.ResponseSpec patchConsent(String channelId, ConsentState target, String reason) {
        return webTestClient
            .patch()
            .uri("/workspaces/{slug}/slack/channels/{c}", workspace.getWorkspaceSlug(), channelId)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateSlackChannelConsentRequestDTO(target, reason))
            .exchange();
    }

    private ConsentState currentState(String channelId) {
        return monitoredChannelRepository
            .findByWorkspaceIdAndSlackChannelId(workspace.getId(), channelId)
            .orElseThrow()
            .getConsentState();
    }

    private Long adminUserId() {
        return userRepository.findByLogin("admin").orElseThrow().getId();
    }

    private void seedChannel(long workspaceId, String channelId, ConsentState state, Instant announcedAt) {
        SlackMonitoredChannel channel = new SlackMonitoredChannel();
        channel.setWorkspaceId(workspaceId);
        channel.setSlackTeamId("T1");
        channel.setSlackChannelId(channelId);
        channel.setConsentState(state);
        channel.setConsentAnnouncedAt(announcedAt);
        monitoredChannelRepository.save(channel);
    }

    private long seedThread(long workspaceId, String channelId, String threadTs) {
        SlackThread thread = new SlackThread();
        thread.setWorkspaceId(workspaceId);
        thread.setSlackChannelId(channelId);
        thread.setSlackThreadTs(threadTs);
        thread.setFirstTs(threadTs);
        thread.setLastTs(threadTs);
        thread.setMessageCount(1);
        return threadRepository.save(thread).getId();
    }

    private UUID lastFeedbackId;

    private UUID seedBoundConversation(long threadId) {
        Practice practice = savePractice();
        AgentJob job = newJob();
        UUID observationId = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            observationId,
            "occ-" + observationId,
            job.getId(),
            practice.getId(),
            null,
            WorkArtifact.CONVERSATION_THREAD.name(),
            threadId,
            owner.getId(),
            "Observation title",
            "ABSENT",
            "BAD",
            "MAJOR",
            0.8f,
            null,
            null,
            null,
            Instant.now()
        );
        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(workspace.getId())
                .artifactType(WorkArtifact.CONVERSATION_THREAD)
                .artifactId(threadId)
                .recipientUserId(owner.getId())
                .aboutUserId(owner.getId())
                .channel(FeedbackChannel.CONVERSATION)
                .position(1)
                .deliveryState(FeedbackDeliveryState.PREPARED)
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .build()
        );
        lastFeedbackId = feedback.getId();
        feedbackObservationRepository.insertIfAbsent(feedback.getId(), observationId, EvidenceRole.PRIMARY.name(), 0);
        return observationId;
    }

    private Practice savePractice() {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug("chan-practice-" + UUID.randomUUID());
        p.setName("Channel Practice");
        p.setCriteria("Test description");
        p.setTriggerEvents(OM.valueToTree(List.of("PullRequestCreated")));
        return practiceRepository.save(p);
    }

    private AgentJob newJob() {
        AgentJob j = new AgentJob();
        j.setWorkspace(workspace);
        j.setJobType(AgentJobType.CONVERSATION_REVIEW);
        j.setConfigSnapshot(OM.valueToTree(Map.of("model", "test")));
        return agentJobRepository.save(j);
    }
}

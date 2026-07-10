package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres, stub-Pi (no live Slack) proof of the conversational delivery loop: two jobs prepare body-null
 * CONVERSATION units; three simulated {@code link_finding} events flip exactly one to DELIVERED (one-per-turn) and
 * bind a CONVERSATION_TURN placement; a re-run is a no-op; a clock-advanced sweep expires the remaining PREPARED
 * units to CONVERSATION_EXPIRED. Deterministic.
 */
class ConversationalFeedbackDeliveryLoopIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private FeedbackChannelRouter router;

    @Autowired
    private ConversationalFeedbackPreparer preparer;

    @Autowired
    private ConversationalDeliveryReconciler reconciler;

    @Autowired
    private ConversationFeedbackTtlSweeper sweeper;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackPlacementRepository feedbackPlacementRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private Workspace workspace;
    private Practice practice;
    private User recipient;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("conv-delivery-test"));
        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OM.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);
        IdentityProvider provider = identityProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                identityProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        recipient = userRepository.save(TestUserFactory.createUser(100L, "recipient", provider));
    }

    @Test
    void twoJobsPrepareConversationUnitsWithNullBody() {
        AgentJob job1 = newJob();
        AgentJob job2 = newJob();
        saveObservation(job1, "occ-1");
        saveObservation(job2, "occ-2");

        prepareFor(job1);
        prepareFor(job2);

        List<Feedback> prepared = feedbackRepository.findRecentPreparedConversationForRecipient(
            workspace.getId(),
            recipient.getId(),
            PageRequest.of(0, 10)
        );
        assertThat(prepared).hasSize(2);
        assertThat(prepared).allSatisfy(f -> {
            assertThat(f.getChannel()).isEqualTo(FeedbackChannel.CONVERSATION);
            assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
            assertThat(f.getBody()).isNull();
        });
    }

    @Test
    void threeLinkFindingsFlipExactlyOne_reRunNoOp_thenSweepExpiresRemainder() {
        AgentJob job = newJob();
        Observation a = saveObservation(job, "occ-a");
        Observation b = saveObservation(job, "occ-b");
        Observation c = saveObservation(job, "occ-c");
        prepareFor(job);
        assertThat(preparedCount()).isEqualTo(3);

        UUID chatMessageId = persistAssistantMessage();
        int flips = reconciler.reconcile(
            workspace.getId(),
            recipient.getId(),
            chatMessageId,
            List.of(a.getId(), b.getId(), c.getId())
        );

        assertThat(flips).isEqualTo(1);
        assertThat(deliveredCount()).isEqualTo(1);
        List<FeedbackPlacement> placements = feedbackPlacementRepository.findAll();
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).getPlacementType()).isEqualTo(PlacementType.CONVERSATION_TURN);
        assertThat(placements.get(0).getChatMessageId()).isEqualTo(chatMessageId);

        // A re-run linking the already-delivered finding is a no-op (guarded CAS returns 0).
        int reflips = reconciler.reconcile(workspace.getId(), recipient.getId(), chatMessageId, List.of(a.getId()));
        assertThat(reflips).isZero();
        assertThat(deliveredCount()).isEqualTo(1);
        assertThat(feedbackPlacementRepository.findAll()).hasSize(1);

        // Advance the clock past the TTL: the two still-PREPARED units expire; the delivered one is untouched.
        long expired = sweeper.sweepNow(
            Instant.now().plus(Duration.ofDays(ConversationFeedbackTtlSweeper.TTL_DAYS + 1))
        );
        assertThat(expired).isEqualTo(2);
        assertThat(preparedCount()).isZero();
        long conversationExpired = conversationUnits()
            .stream()
            .filter(f -> f.getSuppressionReason() == FeedbackSuppressionReason.CONVERSATION_EXPIRED)
            .count();
        assertThat(conversationExpired).isEqualTo(2);

        // Body is NULL on every conversational unit - delivered or expired (composed at delivery, never frozen).
        assertThat(conversationUnits()).allSatisfy(f -> assertThat(f.getBody()).isNull());
    }

    private void prepareFor(AgentJob job) {
        List<Observation> observations = observationRepository.findByAgentJobId(job.getId());
        List<Observation> admitted = router.admit(observations, workspace.getId(), RoutingContext.author());
        preparer.prepare(job.getId(), workspace.getId(), admitted);
    }

    private List<Feedback> conversationUnits() {
        return feedbackRepository
            .findAll()
            .stream()
            .filter(f -> f.getChannel() == FeedbackChannel.CONVERSATION)
            .toList();
    }

    private long preparedCount() {
        return conversationUnits()
            .stream()
            .filter(f -> f.getDeliveryState() == FeedbackDeliveryState.PREPARED)
            .count();
    }

    private long deliveredCount() {
        return conversationUnits()
            .stream()
            .filter(f -> f.getDeliveryState() == FeedbackDeliveryState.DELIVERED)
            .count();
    }

    private AgentJob newJob() {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OM.valueToTree(Map.of("model", "test")));
        return agentJobRepository.save(job);
    }

    private Observation saveObservation(AgentJob job, String occurrenceKey) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            occurrenceKey,
            job.getId(),
            practice.getId(),
            null,
            "PULL_REQUEST",
            42L,
            recipient.getId(),
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
        return observationRepository.findById(id).orElseThrow();
    }

    private UUID persistAssistantMessage() {
        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setUser(recipient);
        thread.setWorkspace(workspace);
        thread.setTitle("t");
        chatThreadRepository.save(thread);
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setThread(thread);
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setStatus(ChatMessage.Status.completed);
        message.setParts(OM.createArrayNode());
        message.setMetadata(OM.createObjectNode());
        chatMessageRepository.save(message);
        return message.getId();
    }
}

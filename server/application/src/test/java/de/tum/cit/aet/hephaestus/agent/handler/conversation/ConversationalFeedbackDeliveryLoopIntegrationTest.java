package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.AdmittedResultFixtures;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChannel;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnPersistence;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ConversationalFeedbackDeliveryLoopIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String SHOWN = "Your description names the decision but not why it beat the alternative.";

    @Autowired
    private FeedbackChannelRouter router;

    @Autowired
    private ConversationalFeedbackPreparer preparer;

    @Autowired
    private ConversationalDeliveryReconciler reconciler;

    @Autowired
    private MentorTurnPersistence mentorTurnPersistence;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackPlacementRepository feedbackPlacementRepository;

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

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

    @Autowired
    private InstanceSettingsService instanceSettingsService;

    private Workspace workspace;
    private Practice practice;
    private User recipient;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        var settings = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(
                false, null, null, EntityTagPrecondition.parse("\"" + settings.getVersion() + "\""));
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("conv-delivery-test"));
        practice = new Practice();
        PracticeTestEvidence.configure(practice, ArtifactKinds.PULL_REQUEST);
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice = practiceRepository.saveAndFlush(practice);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 1));
        practice.setCurrentRevision(revision);
        practice = practiceRepository.saveAndFlush(practice);
        IdentityProvider provider = identityProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> identityProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        recipient = userRepository.save(TestUserFactory.createUser(100L, "recipient", provider));
    }

    @Test
    void twoJobsPrepareStructuredConversationBriefs() {
        AgentJob job1 = newJob();
        AgentJob job2 = newJob();
        saveResult(job1, "occ-1");
        saveResult(job2, "occ-2");

        prepareFor(job1);
        prepareFor(job2);

        List<Feedback> prepared = feedbackRepository.findRecentPreparedConversationForRecipient(
                workspace.getId(), recipient.getId(), PageRequest.of(0, 10));
        assertThat(prepared).hasSize(2);
        assertThat(prepared).allSatisfy(f -> {
            assertThat(f.getChannel()).isEqualTo(FeedbackChannel.IN_CHAT);
            assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
            assertThat(f.getBody()).contains("\"kind\":\"conversation-brief\"");
        });
    }

    @Test
    void shouldDeliverTheFeedbackOnceWhenACompletedReplyShowedIt() {
        AgentJob job = newJob();
        PracticeResult a = saveResult(job, "occ-a");
        PracticeResult b = saveResult(job, "occ-b");
        prepareFor(job);
        ChatMessage assistant = persistAssistantMessage(ChatMessage.Status.in_flight);
        TranslatorState state = new TranslatorState(assistant.getId());
        state.recordDataResult(UIMessageChunk.DataResult.of(a.getId(), SHOWN));
        state.recordDataResult(UIMessageChunk.DataResult.of(b.getId(), SHOWN));
        MentorTurnPersistence.TurnPersistenceCookie cookie = cookie(assistant);
        mentorTurnPersistence.complete(
                cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        mentorTurnPersistence.recordDelivery(cookie, MentorChannel.DeliveryOutcome.DELIVERED);
        mentorTurnPersistence.recordDelivery(cookie, MentorChannel.DeliveryOutcome.DELIVERED);

        assertThat(conversationUnits()).singleElement().satisfies(f -> {
            assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.DELIVERED);
            assertThat(f.getBody()).contains("\"kind\":\"conversation-brief\"");
        });
        assertThat(feedbackPlacementRepository.findAll()).singleElement().satisfies(placement -> {
            assertThat(placement.getPlacementType()).isEqualTo(PlacementType.CONVERSATION_TURN);
            assertThat(placement.getChatMessageId()).isEqualTo(assistant.getId());
        });
        assertThat(chatMessageRepository
                        .findById(assistant.getId())
                        .orElseThrow()
                        .getParts())
                .extracting(part -> part.path("data").path("text").asString())
                .containsExactly(SHOWN, SHOWN);
    }

    @Test
    void shouldLeaveFeedbackPreparedWhenTheReplyOnlyNamedItsResult() {
        AgentJob job = newJob();
        PracticeResult practiceResult = saveResult(job, "occ-named");
        prepareFor(job);
        ChatMessage assistant = persistAssistantMessage(
                ChatMessage.Status.completed,
                OM.createArrayNode()
                        .add(OM.createObjectNode().put("type", "text").put("text", "How did the review go?"))
                        .add(link(practiceResult.getId(), null)));

        mentorTurnPersistence.recordDelivery(cookie(assistant), MentorChannel.DeliveryOutcome.DELIVERED);

        assertThat(preparedCount()).isEqualTo(1);
        assertThat(feedbackPlacementRepository.findAll()).isEmpty();
    }

    @Test
    void shouldDeliverOnlyTheFeedbackTheReplyShowedWhenItAlsoNamedAnother() {
        AgentJob named = newJob();
        PracticeResult onlyNamed = saveResult(named, "occ-only-named");
        prepareFor(named);
        AgentJob shownJob = newJob();
        PracticeResult shown = saveResult(shownJob, "occ-shown");
        prepareFor(shownJob);
        ChatMessage assistant = persistAssistantMessage(
                ChatMessage.Status.completed,
                OM.createArrayNode().add(link(onlyNamed.getId(), null)).add(link(shown.getId(), SHOWN)));

        mentorTurnPersistence.recordDelivery(cookie(assistant), MentorChannel.DeliveryOutcome.DELIVERED);

        assertThat(conversationUnits())
                .extracting(f -> f.getAgentJobId(), f -> f.getDeliveryState())
                .containsExactlyInAnyOrder(
                        tuple(named.getId(), FeedbackDeliveryState.PREPARED),
                        tuple(shownJob.getId(), FeedbackDeliveryState.DELIVERED));
        assertThat(feedbackPlacementRepository.findAll())
                .singleElement()
                .satisfies(placement -> assertThat(placement.getChatMessageId()).isEqualTo(assistant.getId()));
    }

    /** A link to one problem shows the feedback prepared for the result that problem belongs to. */
    @Test
    void shouldDeliverTheResultsFeedbackWhenACompletedReplyShowedOneOfItsProblems() {
        AgentJob job = newJob();
        PracticeResult practiceResult = saveResult(job, "occ-problem-link");
        UUID problemId = UUID.randomUUID();
        observationRepository.insert(
                problemId,
                workspace.getId(),
                practiceResult.getId(),
                0,
                null,
                "MAJOR",
                "Result title",
                "The description names the decision but not why.",
                "[]",
                null,
                null,
                null);
        prepareFor(job);
        ChatMessage assistant = persistAssistantMessage(ChatMessage.Status.in_flight);
        TranslatorState state = new TranslatorState(assistant.getId());
        state.recordDataResultObservation(
                UIMessageChunk.DataResultObservation.of(practiceResult.getId(), problemId, SHOWN));
        MentorTurnPersistence.TurnPersistenceCookie cookie = cookie(assistant);
        mentorTurnPersistence.complete(
                cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        mentorTurnPersistence.recordDelivery(cookie, MentorChannel.DeliveryOutcome.DELIVERED);

        assertThat(conversationUnits())
                .singleElement()
                .satisfies(f -> assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.DELIVERED));
        assertThat(feedbackPlacementRepository.findAll())
                .singleElement()
                .satisfies(placement -> assertThat(placement.getChatMessageId()).isEqualTo(assistant.getId()));
    }

    /** A link stored before protocol 2 named the result in its {@code observationId}; it still settles that result. */
    @Test
    void shouldDeliverTheFeedbackWhenAReplyStoredBeforeProtocolTwoShowedItsResult() {
        AgentJob job = newJob();
        PracticeResult practiceResult = saveResult(job, "occ-legacy-link");
        prepareFor(job);
        ChatMessage assistant = persistAssistantMessage(
                ChatMessage.Status.completed, OM.createArrayNode().add(legacyLink(practiceResult.getId(), SHOWN)));

        mentorTurnPersistence.recordDelivery(cookie(assistant), MentorChannel.DeliveryOutcome.DELIVERED);

        assertThat(conversationUnits())
                .singleElement()
                .satisfies(f -> assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.DELIVERED));
        assertThat(preparedCount()).isZero();
    }

    @Test
    void silentTransportOutcomeConsumesPreparedUnitProspectively() {
        AgentJob job = newJob();
        PracticeResult practiceResult = saveResult(job, "occ-silent");
        prepareFor(job);
        ChatMessage assistant = persistAssistantMessage(ChatMessage.Status.in_flight);
        TranslatorState state = new TranslatorState(assistant.getId());
        state.recordDataResult(UIMessageChunk.DataResult.of(practiceResult.getId(), SHOWN));
        MentorTurnPersistence.TurnPersistenceCookie cookie = cookie(assistant);

        mentorTurnPersistence.complete(
                cookie, state, new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null));

        mentorTurnPersistence.recordDelivery(cookie, MentorChannel.DeliveryOutcome.INSTANCE_SILENCED);

        assertThat(reconciler.suppressForSilentMode(
                        workspace.getId(), recipient.getId(), List.of(practiceResult.getId())))
                .isZero();
        assertThat(chatMessageRepository
                        .findById(assistant.getId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(ChatMessage.Status.completed);
        assertThat(conversationUnits()).singleElement().satisfies(feedback -> {
            assertThat(feedback.getDeliveryState()).isEqualTo(FeedbackDeliveryState.SUPPRESSED);
            assertThat(feedback.getSuppressionReason()).isEqualTo(FeedbackSuppressionReason.INSTANCE_SILENCED);
        });
        assertThat(preparedCount()).isZero();
        assertThat(feedbackPlacementRepository.findAll()).isEmpty();
    }

    /**
     * The decision composition reads before it writes notes is the router's own, keyed by the persisted rows: a problem
     * cited on the diff belongs on the line, and only the unanchored one is prepared for the conversation.
     */
    @Test
    void shouldRouteThePersistedProblemsBeforeCompositionAsPreparationDoes() {
        AgentJob job = newJob();
        PracticeResult onTheDiff = saveResult(job, "occ-diff", "scm.pull-request.diff");
        PracticeResult described = saveResult(job, "occ-described", "scm.pull-request.core");
        List<PracticeResult> persisted = practiceResultRepository.findByAgentJobId(job.getId(), workspace.getId());

        assertThat(router.decide(persisted, workspace.getId(), RoutingContext.author()))
                .containsOnly(
                        Map.entry(onTheDiff.getId(), ConversationRoutingDecision.HAS_INLINE_ANCHOR),
                        Map.entry(described.getId(), ConversationRoutingDecision.ADMIT));
        assertThat(router.decide(persisted, workspace.getId(), RoutingContext.reviewer()))
                .containsOnlyKeys(onTheDiff.getId(), described.getId())
                .doesNotContainValue(ConversationRoutingDecision.ADMIT);

        prepareFor(job);

        assertThat(conversationUnits())
                .filteredOn(unit -> unit.getAgentJobId().equals(job.getId()))
                .singleElement()
                .satisfies(unit -> assertThat(unit.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED));
        assertThat(router.admit(persisted, workspace.getId(), RoutingContext.author()))
                .extracting(PracticeResult::getId)
                .containsExactly(described.getId());
    }

    private void prepareFor(AgentJob job) {
        List<PracticeResult> practiceResults = practiceResultRepository.findByAgentJobId(
                job.getId(), job.getWorkspace().getId());
        List<PracticeResult> admitted = router.admit(practiceResults, workspace.getId(), RoutingContext.author());
        preparer.prepare(job.getId(), workspace.getId(), admitted, List.of(conversationUnit(admitted)));
    }

    private ComposedFeedbackUnit conversationUnit(List<PracticeResult> practiceResults) {
        return new ComposedFeedbackUnit(
                FeedbackChannel.IN_CHAT,
                practice.getSlug(),
                practiceResults.stream().map(o -> o.getId().toString()).toList(),
                ComposedFeedbackUnit.Action.NEW,
                null,
                null,
                "Test practice",
                null,
                null,
                new ComposedFeedbackUnit.ConversationBrief(
                        "The practice recurred.",
                        "Recognize the decision point.",
                        "The results show the same pattern.",
                        "They can explain the decision in their own words.",
                        null));
    }

    private List<Feedback> conversationUnits() {
        return feedbackRepository.findAll().stream()
                .filter(f -> f.getChannel() == FeedbackChannel.IN_CHAT)
                .toList();
    }

    private long preparedCount() {
        return conversationUnits().stream()
                .filter(f -> f.getDeliveryState() == FeedbackDeliveryState.PREPARED)
                .count();
    }

    private AgentJob newJob() {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OM.valueToTree(Map.of("model", "test")));
        job.setEvidenceSnapshot(OM.readTree("{\"manifest\":{\"contractVersion\":\"1.2.0\"}}"));
        return agentJobRepository.save(job);
    }

    private PracticeResult saveResult(AgentJob job, String occurrenceKey) {
        return saveResult(job, occurrenceKey, "scm.pull-request.core");
    }

    private PracticeResult saveResult(AgentJob job, String occurrenceKey, String sourceKind) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                occurrenceKey,
                job.getId(),
                job.getWorkspace().getId(),
                practice.getId(),
                practice.getCurrentRevision().getId(),
                "scm.pull_request",
                42L,
                recipient.getId(),
                "Result title",
                "NOT_MET",
                "MAJOR",
                AdmittedResultFixtures.evidence(
                                job.getId(), sourceKind, "context/metadata.json", "metadata.json", "example")
                        .toString(),
                null,
                "test-scope",
                null,
                null,
                null,
                Instant.now(),
                "LIVE");
        return practiceResultRepository.findById(id).orElseThrow();
    }

    private static MentorTurnPersistence.TurnPersistenceCookie cookie(ChatMessage assistant) {
        return new MentorTurnPersistence.TurnPersistenceCookie(
                assistant.getThread().getId(),
                UUID.randomUUID(),
                assistant.getId(),
                Instant.now(),
                "test-model",
                Mockito.mock(LlmPriceSnapshot.class),
                "test-session-version");
    }

    /** A stored link; one with no text is how replies stored links before they carried the feedback. */
    private static ObjectNode link(UUID practiceResultId, @Nullable String text) {
        ObjectNode part = OM.createObjectNode()
                .put("type", UIMessageChunk.DataResult.PART_TYPE)
                .put("id", UUID.randomUUID().toString());
        ObjectNode data = part.putObject("data").put("practiceResultId", practiceResultId.toString());
        if (text != null) {
            data.put("text", text);
        }
        return part;
    }

    /** A link stored before protocol 2: its {@code observationId} names the practice result. */
    private static ObjectNode legacyLink(UUID practiceResultId, String text) {
        ObjectNode part = OM.createObjectNode()
                .put("type", UIMessageChunk.LinkParts.LEGACY_PART_TYPE)
                .put("id", UUID.randomUUID().toString());
        part.putObject("data").put("observationId", practiceResultId.toString()).put("text", text);
        return part;
    }

    private ChatMessage persistAssistantMessage(ChatMessage.Status status) {
        return persistAssistantMessage(status, OM.createArrayNode());
    }

    private ChatMessage persistAssistantMessage(ChatMessage.Status status, ArrayNode parts) {
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
        message.setStatus(status);
        message.setParts(parts);
        message.setMetadata(OM.createObjectNode());
        return chatMessageRepository.save(message);
    }
}

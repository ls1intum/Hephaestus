package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.ConversationConsentGate;
import de.tum.cit.aet.hephaestus.agent.handler.FeedbackSupersession;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeFeedbackDeliveryPolicy;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Deterministic unit coverage for the conversational-delivery router, preparer, and reconciler. */
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationalDeliveryLoopUnitTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long WS = 1L;
    private static final long RECIPIENT = 7L;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackEvidenceRepository feedbackEvidenceRepository;

    @Mock
    private FeedbackPlacementRepository feedbackPlacementRepository;

    @Mock
    private PracticeResultRepository practiceResultRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboundEgressGuard egressGuard;

    /**
     * Never asked to claim anything here: these cases compose nothing, and a move that is not a SUPERSEDE
     * never reaches it. Present so the preparer can be built.
     */
    @Mock
    private FeedbackSupersession supersession;

    @Mock
    private ResultVisibilityPolicy visibilityPolicy;

    @Mock
    private ConversationConsentGate consentGate;

    /**
     * Only consulted by {@code admit(...)}, which resolves the workspace's defaults before routing. These
     * cases call {@link FeedbackChannelRouter#route} directly and hand it both values, so nothing stubs it.
     */
    @Mock
    private WorkspaceReviewDefaultsProvider workspaceReviewDefaults;

    /**
     * Unless a case says otherwise, every linked id resolves to a readable, authorized result. Both
     * defaults answer <em>from the argument</em> rather than with a fixed row: the reconciler keys its batch
     * by each result's own id, so a stub that handed back an unrelated row would hide a mis-keyed
     * lookup — the batch would look like it worked while admitting the wrong result.
     */
    @BeforeEach
    void authorizeEvidenceDelivery() {
        lenient()
                .when(practiceResultRepository.findAllByIdInAndWorkspaceId(any(), anyLong()))
                .thenAnswer(invocation -> {
                    Collection<UUID> ids = invocation.getArgument(0);
                    // Real instances, not mocks: building a mock inside an Answer would stub one mock from
                    // inside another's invocation, which is how Mockito's ongoing-stubbing state gets corrupted.
                    return ids.stream()
                            .map(id -> PracticeResult.builder()
                                    .scopeKey("test-scope")
                                    .id(id)
                                    .build())
                            .toList();
                });
        lenient()
                .when(visibilityPolicy.permitsForNewDelivery(
                        anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                .thenAnswer(invocation -> {
                    Collection<PracticeResult> practiceResults = invocation.getArgument(1);
                    return practiceResults.stream().map(PracticeResult::getId).collect(Collectors.toSet());
                });
    }

    private FeedbackChannelRouter router() {
        return new FeedbackChannelRouter(feedbackRepository, practiceResultRepository, workspaceReviewDefaults);
    }

    private ConversationalFeedbackPreparer preparer() {
        when(egressGuard.deliveryAllowed(any())).thenReturn(true);
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new ConversationalFeedbackPreparer(
                feedbackRepository,
                feedbackEvidenceRepository,
                practiceResultRepository,
                supersession,
                eventPublisher,
                egressGuard);
    }

    private ConversationalDeliveryReconciler reconciler() {
        return new ConversationalDeliveryReconciler(
                feedbackRepository,
                feedbackEvidenceRepository,
                feedbackPlacementRepository,
                practiceResultRepository,
                visibilityPolicy,
                consentGate);
    }

    @Test
    void admitsALinkOnlyToAResultAboutTheRecipientThatThePolicyStillPermits() {
        UUID owned = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        UUID withdrawn = UUID.randomUUID();
        PracticeResult aboutSomeoneElse = problem(null, foreign);
        when(aboutSomeoneElse.getAboutUserId()).thenReturn(RECIPIENT + 1);
        Map<UUID, PracticeResult> stored = Map.of(
                owned, problem(null, owned),
                foreign, aboutSomeoneElse,
                withdrawn, problem(null, withdrawn));
        doAnswer(invocation -> invocation.<Collection<UUID>>getArgument(0).stream()
                        .map(stored::get)
                        .filter(Objects::nonNull)
                        .toList())
                .when(practiceResultRepository)
                .findAllByIdInAndWorkspaceId(any(), anyLong());
        doAnswer(invocation -> invocation.<Collection<PracticeResult>>getArgument(1).stream()
                        .map(PracticeResult::getId)
                        .filter(id -> !id.equals(withdrawn))
                        .collect(Collectors.toSet()))
                .when(visibilityPolicy)
                .permitsForNewDelivery(anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING));

        assertThat(reconciler().admits(WS, RECIPIENT, owned)).isTrue();
        assertThat(reconciler().admits(WS, RECIPIENT, UUID.randomUUID()))
                .as("no such result")
                .isFalse();
        assertThat(reconciler().admits(WS, RECIPIENT, foreign))
                .as("about another developer")
                .isFalse();
        assertThat(reconciler().admits(WS, RECIPIENT, withdrawn))
                .as("refused by policy")
                .isFalse();
    }

    @Test
    void admitsALinkToAConversationResultOnlyWhileItsConversationIsConsentedTo() {
        UUID practiceResultId = UUID.randomUUID();
        PracticeResult fromConversation = problem(null, practiceResultId);
        when(fromConversation.getArtifactKind()).thenReturn(ArtifactKinds.CONVERSATION_THREAD);
        when(fromConversation.getArtifactId()).thenReturn(55L);
        doReturn(List.of(fromConversation))
                .when(practiceResultRepository)
                .findAllByIdInAndWorkspaceId(any(), anyLong());

        when(consentGate.activeThreadIds(WS, List.of(55L))).thenReturn(Set.of());
        assertThat(reconciler().admits(WS, RECIPIENT, practiceResultId)).isFalse();

        when(consentGate.activeThreadIds(WS, List.of(55L))).thenReturn(Set.of(55L));
        assertThat(reconciler().admits(WS, RECIPIENT, practiceResultId)).isTrue();
    }

    /**
     * Composition reads the lane's routing before it writes notes: the same permission and router preparation uses,
     * so a note about a problem that belongs on a line is never written, and the routing names each admitted row.
     */
    @Test
    void eligibilityRoutesTheAdmittedProblemsThroughThePermissionAndRouterPreparationUses() {
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        AgentJob job = job();
        ObjectNode onTheDiff = MAPPER.createObjectNode();
        onTheDiff.putArray("citations").addObject().put("sourceKind", "scm.pull-request.diff");
        PracticeResult anchored = problem(onTheDiff);
        PracticeResult unanchored = problem(null);
        PracticeResult strength = strength();
        automatic(anchored, unanchored);
        when(egressGuard.deliveryAllowed(any())).thenReturn(true);
        when(policy.allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).thenReturn(true);

        ConversationEligibility eligibility =
                listener(policy).eligibility(job, List.of(anchored, strength, unanchored));

        assertThat(eligibility.state()).isEqualTo(ConversationEligibility.State.COMPLETE);
        assertThat(eligibility.results())
                .containsExactly(
                        new ConversationEligibility.ResultRoute(
                                anchored.getId(), ConversationRoutingDecision.HAS_INLINE_ANCHOR),
                        new ConversationEligibility.ResultRoute(unanchored.getId(), ConversationRoutingDecision.ADMIT));
        assertThat(router().admit(List.of(anchored, strength, unanchored), WS, RoutingContext.author()))
                .containsExactly(unanchored);
    }

    @Test
    void eligibilityIsRefusedOnlyByThePermissionAndUnknownWhenTheProblemsConcernSeveralPeople() {
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        AgentJob job = job();
        PracticeResult mine = problem(null);
        PracticeResult someoneElses = problem(null);
        when(someoneElses.getAboutUserId()).thenReturn(RECIPIENT + 1);

        assertThat(listener(policy).eligibility(job, List.of(mine, someoneElses)))
                .satisfies(read -> {
                    assertThat(read.state()).isEqualTo(ConversationEligibility.State.UNAVAILABLE);
                    assertThat(read.results()).isEmpty();
                });
        verify(policy, never()).allowsComposition(any(), any());

        when(egressGuard.deliveryAllowed(any())).thenReturn(true);
        when(policy.allowsComposition(job, DeliveryPolicySurface.CONVERSATION)).thenReturn(false);
        assertThat(listener(policy).eligibility(job, List.of(mine))).satisfies(read -> {
            assertThat(read.state()).isEqualTo(ConversationEligibility.State.REFUSED);
            assertThat(read.results()).isEmpty();
        });
    }

    private ConversationalDeliveryListener listener(PracticeFeedbackDeliveryPolicy policy) {
        return new ConversationalDeliveryListener(
                practiceResultRepository,
                router(),
                preparer(),
                egressGuard,
                mock(AgentJobRepository.class),
                mock(FeedbackCompositionResultParser.class),
                policy);
    }

    private static AgentJob job() {
        Workspace workspace = new Workspace();
        workspace.setId(WS);
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        return job;
    }

    private void automatic(PracticeResult... practiceResults) {
        when(workspaceReviewDefaults.forWorkspace(WS))
                .thenReturn(new WorkspaceReviewDefaults(PracticeAutonomy.AUTOMATIC));
        List<PracticeResultRepository.ResultPracticeAutonomy> rows = new ArrayList<>();
        for (PracticeResult practiceResult : practiceResults) {
            UUID practiceResultId = practiceResult.getId();
            PracticeResultRepository.ResultPracticeAutonomy row =
                    mock(PracticeResultRepository.ResultPracticeAutonomy.class);
            when(row.getPracticeResultId()).thenReturn(practiceResultId);
            when(row.getPracticeAutonomy()).thenReturn(PracticeAutonomy.AUTOMATIC);
            rows.add(row);
        }
        when(practiceResultRepository.findPracticeAutonomyFor(any(), eq(WS))).thenReturn(rows);
    }

    @Test
    void shouldCreateNoFutureWorkWhenSilentModeIsEngaged() {
        ConversationalFeedbackPreparer preparer = preparer();
        when(egressGuard.deliveryAllowed(any())).thenReturn(false);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(problem(null)), List.of());

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private enum ObsKind {
        PROBLEM_NO_ANCHOR,
        PROBLEM_FILE_ANCHOR,
        PROBLEM_NON_DIFF_LOCATION,
        STRENGTH,
        NOT_APPLICABLE,
        ALREADY_DELIVERED,
    }

    static Stream<Arguments> routerCases() {
        return Stream.of(
                arguments(ObsKind.PROBLEM_NO_ANCHOR, false, ConversationRoutingDecision.ADMIT),
                arguments(ObsKind.PROBLEM_NO_ANCHOR, true, ConversationRoutingDecision.REVIEWER_DEFERRED),
                arguments(ObsKind.STRENGTH, false, ConversationRoutingDecision.NOT_DELIVERABLE),
                arguments(ObsKind.NOT_APPLICABLE, false, ConversationRoutingDecision.NOT_DELIVERABLE),
                arguments(ObsKind.PROBLEM_FILE_ANCHOR, false, ConversationRoutingDecision.HAS_INLINE_ANCHOR),
                arguments(ObsKind.PROBLEM_NON_DIFF_LOCATION, false, ConversationRoutingDecision.ADMIT),
                arguments(ObsKind.ALREADY_DELIVERED, false, ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT));
    }

    @ParameterizedTest
    @MethodSource("routerCases")
    void routerMapsResultToDecision(ObsKind kind, boolean reviewer, ConversationRoutingDecision expected) {
        RoutingContext ctx = reviewer ? RoutingContext.reviewer() : RoutingContext.author();
        PracticeResult obs =
                switch (kind) {
                    case PROBLEM_NO_ANCHOR -> problem(null);
                    case PROBLEM_FILE_ANCHOR -> {
                        ObjectNode evidence = MAPPER.createObjectNode();
                        evidence.putArray("citations")
                                .addObject()
                                .put("sourceKind", "scm.pull-request.diff")
                                .put("path", "src/Main.java");
                        yield problem(evidence);
                    }
                    case PROBLEM_NON_DIFF_LOCATION -> {
                        ObjectNode evidence = MAPPER.createObjectNode();
                        evidence.putArray("citations")
                                .addObject()
                                .put("sourceKind", "scm.pull-request.core")
                                .put("path", "pull-request.json");
                        yield problem(evidence);
                    }
                    case STRENGTH -> strength();
                    case NOT_APPLICABLE -> notApplicable();
                    case ALREADY_DELIVERED -> {
                        PracticeResult delivered = problem(null);
                        when(feedbackRepository.existsDeliveredInContextForResult(WS, RECIPIENT, delivered.getId()))
                                .thenReturn(true);
                        yield delivered;
                    }
                };

        assertThat(router().route(obs, PracticeAutonomy.AUTOMATIC, WS, ctx)).isEqualTo(expected);
    }

    @Test
    void shouldAdmitDifferentResultWhenItsLocationWasAlreadyDelivered() {
        PracticeResult delivered = problem(null);
        PracticeResult otherBehavior = problem(null);
        when(feedbackRepository.existsDeliveredInContextForResult(WS, RECIPIENT, delivered.getId()))
                .thenReturn(true);
        when(feedbackRepository.existsDeliveredInContextForResult(WS, RECIPIENT, otherBehavior.getId()))
                .thenReturn(false);
        assertThat(router().route(delivered, PracticeAutonomy.AUTOMATIC, WS, RoutingContext.author()))
                .isEqualTo(ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT);
        assertThat(router().route(otherBehavior, PracticeAutonomy.AUTOMATIC, WS, RoutingContext.author()))
                .isEqualTo(ConversationRoutingDecision.ADMIT);
    }

    /**
     * Coaching a developer in a mentor turn about a decision they made months ago presents retrospective
     * measurement as though it were today's work. Asked before the autonomy, because it needs no lookup and
     * no per-practice dial can undo it.
     */
    @Test
    void aBackfilledResultIsNeverRaisedInAMentorTurn() {
        PracticeResult obs = problem(null);
        lenient().when(obs.getOrigin()).thenReturn(ResultOrigin.BACKFILL);

        assertThat(router().route(obs, PracticeAutonomy.AUTOMATIC, WS, RoutingContext.author()))
                .isEqualTo(ConversationRoutingDecision.BACKFILL_QUIET);
    }

    @Test
    void preparerDoesNotInventBriefsWhenCompositionIsMissing() {
        UUID job = UUID.randomUUID();
        List<PracticeResult> admitted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            admitted.add(problem(null)); // single recipient, capped at 3
        }

        int prepared = preparer().prepare(job, WS, admitted, List.of());

        assertThat(prepared).isZero();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void preparerReRunPublishesNoEvent() {
        ConversationalFeedbackPreparer preparer = preparer();
        // Every (job, position) already exists → pure idempotent re-run, nothing newly prepared.
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(true);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(problem(null)), List.of());

        assertThat(prepared).isZero();
        // any(Object.class), not any(): binds the publishEvent(Object) overload the record actually dispatches to.
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void reconcilerFlipsExactlyOnePerTurnAndPlacesIt() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        UUID msg = UUID.randomUUID();
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, a))
                .thenReturn(List.of(fidA));
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(1);
        when(feedbackRepository.getReferenceById(fidA)).thenReturn(mock(Feedback.class));

        int flips = reconciler().reconcile(WS, RECIPIENT, msg, List.of(a, b, c));

        assertThat(flips).isEqualTo(1);
        verify(feedbackRepository, times(1)).markConversationDelivered(eq(fidA), any());
        // one-per-turn cap: b/c are never even looked up after the first winning flip.
        verify(feedbackEvidenceRepository, never()).findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, b);
        ArgumentCaptor<FeedbackPlacement> placement = ArgumentCaptor.forClass(FeedbackPlacement.class);
        verify(feedbackPlacementRepository, times(1)).save(placement.capture());
        assertThat(placement.getValue().getPlacementType()).isEqualTo(PlacementType.CONVERSATION_TURN);
        assertThat(placement.getValue().getChatMessageId()).isEqualTo(msg);
    }

    @Test
    void reconcilerReRunIsNoOpWhenAlreadyDelivered() {
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, a))
                .thenReturn(List.of(fidA));
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(0);

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isZero();
        verify(feedbackPlacementRepository, never()).save(any());
    }

    @Test
    void reconcilerWithholdsPreparedFeedbackAfterAuthorizationWithdrawal() {
        UUID practiceResultId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        PracticeResult practiceResult = problem(null, practiceResultId);
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, practiceResultId))
                .thenReturn(List.of(feedbackId));
        doReturn(List.of(practiceResult)).when(practiceResultRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        // Withheld = absent from the permitted set. Nothing else in the batch says so.
        doReturn(Set.of())
                .when(visibilityPolicy)
                .permitsForNewDelivery(anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING));

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(practiceResultId));

        assertThat(flips).isZero();
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
    }

    /**
     * The silent-mode ending refuses what the delivering ending refuses. Suppression is not a disclosure,
     * so nothing leaks here — but it is the <em>irreversible</em> half: nothing ever writes a unit back to
     * PREPARED, so burning one on a linked id the policy refuses spends coaching the turn was never allowed
     * to raise, and records "the instance silenced this" about a unit silent mode did not decide. Left
     * PREPARED, the same unit is still settled — by the TTL sweep, with a reason of its own.
     */
    @Test
    void silentModeSuppressesNothingWhenTheLinkedResultIsRefused() {
        UUID practiceResultId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        PracticeResult practiceResult = problem(null, practiceResultId);
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, practiceResultId))
                .thenReturn(List.of(feedbackId));
        doReturn(List.of(practiceResult)).when(practiceResultRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        doReturn(Set.of())
                .when(visibilityPolicy)
                .permitsForNewDelivery(anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING));

        int suppressed = reconciler().suppressForSilentMode(WS, RECIPIENT, List.of(practiceResultId));

        assertThat(suppressed).isZero();
        verify(feedbackRepository, never()).markConversationSuppressedBySilentMode(any());
    }

    @Test
    void silentModeSuppressesOnePreparedUnitWithoutPlacement() {
        UUID practiceResultId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, practiceResultId))
                .thenReturn(List.of(feedbackId));
        when(feedbackRepository.markConversationSuppressedBySilentMode(feedbackId))
                .thenReturn(1);

        int suppressed = reconciler().suppressForSilentMode(WS, RECIPIENT, List.of(practiceResultId));

        assertThat(suppressed).isEqualTo(1);
        verify(feedbackRepository).markConversationSuppressedBySilentMode(feedbackId);
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
        verify(feedbackPlacementRepository, never()).save(any());
    }

    @Test
    void reconcilerSkipsFlip_whenLocusWasSinceDeliveredInContext() {
        // A PREPARED unit seeded by a FAILED direct delivery: if this exact result was delivered
        // in-context, the flip must be skipped (no double-delivery) — the stale unit ages out.
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, a))
                .thenReturn(List.of(fidA));
        PracticeResult obs = problem(null, a);
        doReturn(List.of(obs)).when(practiceResultRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        when(feedbackRepository.existsDeliveredInContextForResult(WS, RECIPIENT, a))
                .thenReturn(true);

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isZero();
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
    }

    @Test
    void reconcilerStillFlips_whenLocusWasNotDeliveredInContext() {
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(WS, RECIPIENT, a))
                .thenReturn(List.of(fidA));
        PracticeResult obs = problem(null, a);
        doReturn(List.of(obs)).when(practiceResultRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        when(feedbackRepository.existsDeliveredInContextForResult(WS, RECIPIENT, a))
                .thenReturn(false);
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(1);
        when(feedbackRepository.getReferenceById(fidA)).thenReturn(mock(Feedback.class));

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isEqualTo(1);
        verify(feedbackRepository).markConversationDelivered(eq(fidA), any());
    }

    @ParameterizedTest
    @MethodSource("autonomyRoutingCases")
    void routerAppliesAutonomyBeforeAnythingElse(PracticeAutonomy autonomy, ConversationRoutingDecision expected) {
        PracticeResult practiceResult = problem(null);

        assertThat(router().route(practiceResult, autonomy, WS, RoutingContext.author()))
                .isEqualTo(expected);
    }

    static Stream<Arguments> autonomyRoutingCases() {
        // A chat turn is read on request, so approval gates only what is pushed: HUMAN_APPROVAL delivers here.
        return Stream.of(
                arguments(PracticeAutonomy.OFF, ConversationRoutingDecision.PRACTICE_REQUIRES_APPROVAL),
                arguments(PracticeAutonomy.HUMAN_APPROVAL, ConversationRoutingDecision.ADMIT),
                arguments(PracticeAutonomy.AUTOMATIC, ConversationRoutingDecision.ADMIT));
    }

    @Test
    void autonomyIsAppliedBeforeReviewerDeferral() {
        PracticeResult practiceResult = problem(null);

        assertThat(router().route(practiceResult, PracticeAutonomy.OFF, WS, RoutingContext.reviewer()))
                .isEqualTo(ConversationRoutingDecision.PRACTICE_REQUIRES_APPROVAL);
        assertThat(router().route(practiceResult, PracticeAutonomy.HUMAN_APPROVAL, WS, RoutingContext.reviewer()))
                .isEqualTo(ConversationRoutingDecision.REVIEWER_DEFERRED);
    }

    @Test
    void anUnresolvedAutonomyFailsClosed() {
        PracticeResult practiceResult = problem(null);

        assertThat(router().route(practiceResult, null, WS, RoutingContext.author()))
                .isEqualTo(ConversationRoutingDecision.PRACTICE_REQUIRES_APPROVAL);
    }

    private PracticeResult problem(@Nullable ObjectNode evidence) {
        return problem(evidence, UUID.randomUUID());
    }

    private PracticeResult problem(@Nullable ObjectNode evidence, UUID id) {
        PracticeResult o = mock(PracticeResult.class);

        lenient().when(o.getId()).thenReturn(id);
        lenient().when(o.getOutcome()).thenReturn(Outcome.NOT_MET);
        lenient().when(o.getSeverity()).thenReturn(Severity.MAJOR);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getArtifactId()).thenReturn(100L);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getEvidence()).thenReturn(evidence);
        lenient().when(o.getOrigin()).thenReturn(ResultOrigin.LIVE);
        return o;
    }

    private PracticeResult strength() {
        PracticeResult o = mock(PracticeResult.class);

        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getOutcome()).thenReturn(Outcome.MET);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getOrigin()).thenReturn(ResultOrigin.LIVE);
        return o;
    }

    private PracticeResult notApplicable() {
        PracticeResult o = mock(PracticeResult.class);

        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getOutcome()).thenReturn(Outcome.NOT_APPLICABLE);

        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getOrigin()).thenReturn(ResultOrigin.LIVE);
        return o;
    }
}

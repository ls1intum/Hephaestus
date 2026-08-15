package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Deterministic unit coverage for the conversational-delivery router, preparer, and reconciler. */
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ConversationalDeliveryLoopUnitTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long WS = 1L;
    private static final long RECIPIENT = 7L;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackObservationRepository feedbackObservationRepository;

    @Mock
    private FeedbackPlacementRepository feedbackPlacementRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository observationRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboundEgressGuard egressGuard;

    @Mock
    private ObservationVisibilityPolicy visibilityPolicy;

    /**
     * Only consulted by {@code admit(...)}, which resolves the workspace's defaults before routing. These
     * cases call {@link FeedbackChannelRouter#route} directly and hand it both values, so nothing stubs it.
     */
    @Mock
    private WorkspaceReviewDefaultsProvider workspaceReviewDefaults;

    /**
     * Unless a case says otherwise, every linked id resolves to a readable, authorized observation. Both
     * defaults answer <em>from the argument</em> rather than with a fixed row: the reconciler keys its batch
     * by each observation's own id, so a stub that handed back an unrelated row would hide a mis-keyed
     * lookup — the batch would look like it worked while admitting the wrong observation.
     */
    @BeforeEach
    void authorizeEvidenceDelivery() {
        lenient()
            .when(observationRepository.findAllByIdInAndWorkspaceId(any(), anyLong()))
            .thenAnswer(invocation -> {
                Collection<UUID> ids = invocation.getArgument(0);
                // Real instances, not mocks: building a mock inside an Answer would stub one mock from
                // inside another's invocation, which is how Mockito's ongoing-stubbing state gets corrupted.
                return ids
                    .stream()
                    .map(id -> Observation.builder().id(id).build())
                    .toList();
            });
        lenient()
            .when(visibilityPolicy.permitsAll(anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
            .thenAnswer(invocation -> {
                Collection<Observation> observations = invocation.getArgument(1);
                return observations.stream().map(Observation::getId).collect(Collectors.toSet());
            });
    }

    private FeedbackChannelRouter router() {
        return new FeedbackChannelRouter(feedbackRepository, observationRepository, workspaceReviewDefaults);
    }

    private ConversationalFeedbackPreparer preparer() {
        when(egressGuard.deliveryAllowed(any())).thenReturn(true);
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new ConversationalFeedbackPreparer(
            feedbackRepository,
            feedbackObservationRepository,
            eventPublisher,
            egressGuard
        );
    }

    private ConversationalDeliveryReconciler reconciler() {
        return new ConversationalDeliveryReconciler(
            feedbackRepository,
            feedbackObservationRepository,
            feedbackPlacementRepository,
            observationRepository,
            visibilityPolicy
        );
    }

    @Test
    void shouldCreateNoFutureWorkWhenSilentModeIsEngaged() {
        ConversationalFeedbackPreparer preparer = preparer();
        when(egressGuard.deliveryAllowed(any())).thenReturn(false);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(problem(null, "rk-silent")));

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
            arguments(ObsKind.ALREADY_DELIVERED, false, ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT)
        );
    }

    @ParameterizedTest
    @MethodSource("routerCases")
    void routerMapsObservationToDecision(ObsKind kind, boolean reviewer, ConversationRoutingDecision expected) {
        RoutingContext ctx = reviewer ? RoutingContext.reviewer() : RoutingContext.author();
        Observation obs = switch (kind) {
            case PROBLEM_NO_ANCHOR -> problem(null, null);
            case PROBLEM_FILE_ANCHOR -> {
                ObjectNode evidence = MAPPER.createObjectNode();
                evidence
                    .putArray("citations")
                    .addObject()
                    .put("sourceKind", "scm.pull-request.diff")
                    .put("path", "src/Main.java");
                yield problem(evidence, null);
            }
            case PROBLEM_NON_DIFF_LOCATION -> {
                ObjectNode evidence = MAPPER.createObjectNode();
                evidence
                    .putArray("citations")
                    .addObject()
                    .put("sourceKind", "scm.pull-request.core")
                    .put("path", "pull-request.json");
                yield problem(evidence, null);
            }
            case STRENGTH -> strength();
            case NOT_APPLICABLE -> notApplicable();
            case ALREADY_DELIVERED -> {
                when(feedbackRepository.existsDeliveredInContextForRecurrenceKey(WS, RECIPIENT, "rk-1")).thenReturn(
                    true
                );
                yield problem(null, "rk-1");
            }
        };

        assertThat(router().route(obs, PracticeReviewTier.DELIVER, FeedbackReach.ON_THE_WORK, WS, ctx)).isEqualTo(
            expected
        );
    }

    /**
     * Coaching a developer in a mentor turn about a decision they made months ago presents retrospective
     * measurement as though it were today's work. Asked before the tier, because it needs no lookup and
     * no per-practice dial can undo it.
     */
    @Test
    void aBackfilledObservationIsNeverRaisedInAMentorTurn() {
        Observation obs = problem(null, null);
        lenient().when(obs.getOrigin()).thenReturn(ObservationOrigin.BACKFILL);

        assertThat(
            router().route(obs, PracticeReviewTier.DELIVER, FeedbackReach.ON_THE_WORK, WS, RoutingContext.author())
        ).isEqualTo(ConversationRoutingDecision.BACKFILL_QUIET);
    }

    @Test
    void preparerWritesPreparedUnitsBodyNullCappedAtThree() {
        UUID job = UUID.randomUUID();
        List<Observation> admitted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            admitted.add(problem(null, null, 0.9f - i * 0.1f));
        }

        int prepared = preparer().prepare(job, WS, admitted);

        // Three are raised; the two over the cap are withheld — and a withheld locus the router admitted still
        // gets a row, or it would read as feedback the developer saw and ignored.
        assertThat(prepared).isEqualTo(3);
        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, times(5)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(f -> {
            assertThat(f.getChannel()).isEqualTo(FeedbackChannel.CONVERSATION);
            assertThat(f.getBody()).isNull();
            assertThat(f.getRecipientUserId()).isEqualTo(RECIPIENT);
        });
        assertThat(saved.getAllValues())
            .extracting(Feedback::getDeliveryState, Feedback::getSuppressionReason, Feedback::getPosition)
            .containsExactly(
                tuple(FeedbackDeliveryState.PREPARED, null, 3000),
                tuple(FeedbackDeliveryState.PREPARED, null, 3001),
                tuple(FeedbackDeliveryState.PREPARED, null, 3002),
                tuple(FeedbackDeliveryState.SUPPRESSED, FeedbackSuppressionReason.VOLUME_CAPPED, 3003),
                tuple(FeedbackDeliveryState.SUPPRESSED, FeedbackSuppressionReason.VOLUME_CAPPED, 3004)
            );
    }

    @Test
    void preparerFailsLoud_ratherThanOverflowItsOrdinalBand() {
        // Every admitted observation takes a slot, so a pathological job could run the band into the next
        // one — where the (agent_job_id, position) guard would read a foreign row as "already recorded" and
        // drop the write. That is the silent drop this whole ledger exists to prevent, so it must be loud.
        UUID job = UUID.randomUUID();
        List<Observation> admitted = new ArrayList<>();
        for (int i = 0; i < FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH + 1; i++) {
            admitted.add(problem(null, null, 0.5f));
        }

        assertThatThrownBy(() -> preparer().prepare(job, WS, admitted))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ordinal band");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void preparerPublishesOnePreparedEventPerRecipient_countingOnlyNewUnits() {
        UUID job = UUID.randomUUID();
        List<Observation> admitted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            admitted.add(problem(null, null, 0.9f - i * 0.1f)); // single recipient, capped at 3
        }

        preparer().prepare(job, WS, admitted);

        ArgumentCaptor<ConversationFeedbackPreparedEvent> event = ArgumentCaptor.forClass(
            ConversationFeedbackPreparedEvent.class
        );
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new ConversationFeedbackPreparedEvent(WS, RECIPIENT, 3));
    }

    @Test
    void preparerReRunPublishesNoEvent() {
        ConversationalFeedbackPreparer preparer = preparer();
        // Every (job, position) already exists → pure idempotent re-run, nothing newly prepared.
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(true);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(problem(null, null, 0.9f)));

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
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, a)
        ).thenReturn(List.of(fidA));
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(1);
        when(feedbackRepository.getReferenceById(fidA)).thenReturn(mock(Feedback.class));

        int flips = reconciler().reconcile(WS, RECIPIENT, msg, List.of(a, b, c));

        assertThat(flips).isEqualTo(1);
        verify(feedbackRepository, times(1)).markConversationDelivered(eq(fidA), any());
        // one-per-turn cap: b/c are never even looked up after the first winning flip.
        verify(feedbackObservationRepository, never()).findPreparedConversationFeedbackIdsByObservation(
            WS,
            RECIPIENT,
            b
        );
        ArgumentCaptor<FeedbackPlacement> placement = ArgumentCaptor.forClass(FeedbackPlacement.class);
        verify(feedbackPlacementRepository, times(1)).save(placement.capture());
        assertThat(placement.getValue().getPlacementType()).isEqualTo(PlacementType.CONVERSATION_TURN);
        assertThat(placement.getValue().getChatMessageId()).isEqualTo(msg);
    }

    @Test
    void reconcilerReRunIsNoOpWhenAlreadyDelivered() {
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, a)
        ).thenReturn(List.of(fidA));
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(0);

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isZero();
        verify(feedbackPlacementRepository, never()).save(any());
    }

    @Test
    void reconcilerWithholdsPreparedFeedbackAfterAuthorizationWithdrawal() {
        UUID observationId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        Observation observation = problem(null, null, 0.9f, observationId);
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, observationId)
        ).thenReturn(List.of(feedbackId));
        doReturn(List.of(observation)).when(observationRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        // Withheld = absent from the permitted set. Nothing else in the batch says so.
        doReturn(Set.of())
            .when(visibilityPolicy)
            .permitsAll(anyLong(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING));

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(observationId));

        assertThat(flips).isZero();
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
    }

    @Test
    void silentModeSuppressesOnePreparedUnitWithoutPlacement() {
        UUID observationId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, observationId)
        ).thenReturn(List.of(feedbackId));
        when(feedbackRepository.markConversationSuppressedBySilentMode(feedbackId)).thenReturn(1);

        int suppressed = reconciler().suppressForSilentMode(WS, RECIPIENT, List.of(observationId));

        assertThat(suppressed).isEqualTo(1);
        verify(feedbackRepository).markConversationSuppressedBySilentMode(feedbackId);
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
        verify(feedbackPlacementRepository, never()).save(any());
    }

    @Test
    void reconcilerSkipsFlip_whenLocusWasSinceDeliveredInContext() {
        // A PREPARED unit seeded by a FAILED direct delivery: if a later re-review has since delivered the SAME
        // recurrence_key in-context, the flip must be skipped (no double-delivery) — the stale unit ages out.
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, a)
        ).thenReturn(List.of(fidA));
        Observation obs = problem(null, "rk-delivered", 0.9f, a);
        doReturn(List.of(obs)).when(observationRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        when(feedbackRepository.existsDeliveredInContextForRecurrenceKey(WS, RECIPIENT, "rk-delivered")).thenReturn(
            true
        );

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isZero();
        verify(feedbackRepository, never()).markConversationDelivered(any(), any());
    }

    @Test
    void reconcilerStillFlips_whenLocusHasKeyButWasNotDeliveredInContext() {
        UUID a = UUID.randomUUID();
        UUID fidA = UUID.randomUUID();
        when(
            feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(WS, RECIPIENT, a)
        ).thenReturn(List.of(fidA));
        Observation obs = problem(null, "rk-fresh", 0.9f, a);
        doReturn(List.of(obs)).when(observationRepository).findAllByIdInAndWorkspaceId(any(), anyLong());
        when(feedbackRepository.existsDeliveredInContextForRecurrenceKey(WS, RECIPIENT, "rk-fresh")).thenReturn(false);
        when(feedbackRepository.markConversationDelivered(eq(fidA), any())).thenReturn(1);
        when(feedbackRepository.getReferenceById(fidA)).thenReturn(mock(Feedback.class));

        int flips = reconciler().reconcile(WS, RECIPIENT, UUID.randomUUID(), List.of(a));

        assertThat(flips).isEqualTo(1);
        verify(feedbackRepository).markConversationDelivered(eq(fidA), any());
    }

    /**
     * The autonomy tier's conversation half. Only DELIVER may speak without being asked; PROPOSE promises
     * silence on every channel and must be refused here — and refused with a NAMED reason, because "the
     * workspace turned this practice down" is a different answer to "why did nothing happen" than "there
     * was nothing worth raising".
     */
    @ParameterizedTest
    @MethodSource("tierRoutingCases")
    void routerAppliesTheAutonomyTierBeforeAnythingElse(PracticeReviewTier tier, ConversationRoutingDecision expected) {
        Observation observation = problem(null, null);

        assertThat(router().route(observation, tier, FeedbackReach.ON_THE_WORK, WS, RoutingContext.author())).isEqualTo(
            expected
        );
    }

    static Stream<Arguments> tierRoutingCases() {
        return Stream.of(
            arguments(PracticeReviewTier.OFF, ConversationRoutingDecision.PRACTICE_TIER_QUIET),
            arguments(PracticeReviewTier.PROPOSE, ConversationRoutingDecision.PRACTICE_TIER_QUIET),
            arguments(PracticeReviewTier.DELIVER, ConversationRoutingDecision.ADMIT)
        );
    }

    /**
     * The reach axis is orthogonal to the tier: a workspace that keeps feedback out of the work still
     * coaches in the mentor conversation, which is the whole point of narrowing reach rather than turning
     * practices down. Both reaches therefore admit here.
     */
    @ParameterizedTest
    @MethodSource("reachRoutingCases")
    void everyReachStillOpensTheMentorConversation(FeedbackReach reach) {
        Observation observation = problem(null, null);

        assertThat(
            router().route(observation, PracticeReviewTier.DELIVER, reach, WS, RoutingContext.author())
        ).isEqualTo(ConversationRoutingDecision.ADMIT);
    }

    static Stream<Arguments> reachRoutingCases() {
        return Stream.of(arguments(FeedbackReach.CONVERSATION), arguments(FeedbackReach.ON_THE_WORK));
    }

    /**
     * A tier-quiet observation is refused even when it is reviewer-targeted, i.e. the tier is asked first.
     * That ordering matters for the trace view: the standing workspace policy is the more useful answer.
     */
    @Test
    void tierIsAskedBeforeTheReviewerDeferral() {
        Observation observation = problem(null, null);

        assertThat(
            router().route(
                observation,
                PracticeReviewTier.PROPOSE,
                FeedbackReach.ON_THE_WORK,
                WS,
                RoutingContext.reviewer()
            )
        ).isEqualTo(ConversationRoutingDecision.PRACTICE_TIER_QUIET);
    }

    /**
     * A tier the projection could not resolve must not silence the observation: a lookup miss is our
     * problem, and withholding coaching over it would be a refusal nobody asked for.
     */
    @Test
    void anUnresolvedTierLeavesTheRemainingRulesInCharge() {
        Observation observation = problem(null, null);

        assertThat(router().route(observation, null, FeedbackReach.ON_THE_WORK, WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.ADMIT
        );
    }

    private Observation problem(ObjectNode evidence, String recurrenceKey) {
        return problem(evidence, recurrenceKey, 0.9f);
    }

    private Observation problem(ObjectNode evidence, String recurrenceKey, float confidence) {
        return problem(evidence, recurrenceKey, confidence, UUID.randomUUID());
    }

    private Observation problem(ObjectNode evidence, String recurrenceKey, float confidence, UUID id) {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(id);
        lenient().when(o.getPresence()).thenReturn(Presence.ABSENT);
        lenient().when(o.getAssessment()).thenReturn(Assessment.BAD);
        lenient().when(o.getSeverity()).thenReturn(Severity.MAJOR);
        lenient().when(o.getConfidence()).thenReturn(confidence);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getArtifactId()).thenReturn(100L);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getEvidence()).thenReturn(evidence);
        lenient().when(o.getRecurrenceKey()).thenReturn(recurrenceKey);
        lenient().when(o.getOrigin()).thenReturn(ObservationOrigin.LIVE);
        return o;
    }

    private Observation strength() {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getPresence()).thenReturn(Presence.PRESENT);
        lenient().when(o.getAssessment()).thenReturn(Assessment.GOOD);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getOrigin()).thenReturn(ObservationOrigin.LIVE);
        return o;
    }

    private Observation notApplicable() {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getPresence()).thenReturn(Presence.NOT_APPLICABLE);
        lenient().when(o.getAssessment()).thenReturn(null);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(o.getOrigin()).thenReturn(ObservationOrigin.LIVE);
        return o;
    }
}

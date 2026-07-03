package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Deterministic unit coverage for the S7 conversational-delivery router, preparer, and reconciler. */
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

    private FeedbackChannelRouter router() {
        return new FeedbackChannelRouter(feedbackRepository);
    }

    private ConversationalFeedbackPreparer preparer() {
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(false);
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new ConversationalFeedbackPreparer(feedbackRepository, feedbackObservationRepository);
    }

    private ConversationalDeliveryReconciler reconciler() {
        return new ConversationalDeliveryReconciler(
            feedbackRepository,
            feedbackObservationRepository,
            feedbackPlacementRepository
        );
    }

    @Test
    void routerAdmitsAuthorProblemWithNoInlineAnchor() {
        assertThat(router().route(problem(null, null), WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.ADMIT
        );
    }

    @Test
    void routerDefersReviewerTargeted() {
        assertThat(router().route(problem(null, null), WS, RoutingContext.reviewer())).isEqualTo(
            ConversationRoutingDecision.REVIEWER_DEFERRED
        );
    }

    @Test
    void routerRejectsStrengthAndNotApplicable() {
        assertThat(router().route(strength(), WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.NOT_DELIVERABLE
        );
        assertThat(router().route(notApplicable(), WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.NOT_DELIVERABLE
        );
    }

    @Test
    void routerRejectsPrObservationWithFileLocation() {
        ObjectNode evidence = MAPPER.createObjectNode();
        evidence.putArray("locations").addObject().put("path", "src/Main.java");
        assertThat(router().route(problem(evidence, null), WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.HAS_INLINE_ANCHOR
        );
    }

    @Test
    void routerRejectsWhenAlreadyDeliveredInContext() {
        when(feedbackRepository.existsDeliveredInContextForRecurrenceKey(WS, RECIPIENT, "rk-1")).thenReturn(true);
        assertThat(router().route(problem(null, "rk-1"), WS, RoutingContext.author())).isEqualTo(
            ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT
        );
    }

    @Test
    void preparerWritesPreparedUnitsBodyNullCappedAtThree() {
        UUID job = UUID.randomUUID();
        List<Observation> admitted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            admitted.add(problem(null, null, 0.9f - i * 0.1f));
        }

        int prepared = preparer().prepare(job, WS, admitted);

        assertThat(prepared).isEqualTo(3);
        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(f -> {
            assertThat(f.getChannel()).isEqualTo(FeedbackChannel.CONVERSATION);
            assertThat(f.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
            assertThat(f.getBody()).isNull();
            assertThat(f.getRecipientUserId()).isEqualTo(RECIPIENT);
        });
        assertThat(saved.getAllValues()).extracting(Feedback::getPosition).containsExactly(3000, 3001, 3002);
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

    private Observation problem(ObjectNode evidence, String recurrenceKey) {
        return problem(evidence, recurrenceKey, 0.9f);
    }

    private Observation problem(ObjectNode evidence, String recurrenceKey, float confidence) {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getPresence()).thenReturn(Presence.ABSENT);
        lenient().when(o.getAssessment()).thenReturn(Assessment.BAD);
        lenient().when(o.getSeverity()).thenReturn(Severity.MAJOR);
        lenient().when(o.getConfidence()).thenReturn(confidence);
        lenient().when(o.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        lenient().when(o.getArtifactId()).thenReturn(100L);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getEvidence()).thenReturn(evidence);
        lenient().when(o.getRecurrenceKey()).thenReturn(recurrenceKey);
        return o;
    }

    private Observation strength() {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getPresence()).thenReturn(Presence.PRESENT);
        lenient().when(o.getAssessment()).thenReturn(Assessment.GOOD);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        return o;
    }

    private Observation notApplicable() {
        Observation o = mock(Observation.class);
        lenient().when(o.getId()).thenReturn(UUID.randomUUID());
        lenient().when(o.getPresence()).thenReturn(Presence.NOT_APPLICABLE);
        lenient().when(o.getAssessment()).thenReturn(null);
        lenient().when(o.getAboutUserId()).thenReturn(RECIPIENT);
        lenient().when(o.getArtifactType()).thenReturn(WorkArtifact.PULL_REQUEST);
        return o;
    }
}

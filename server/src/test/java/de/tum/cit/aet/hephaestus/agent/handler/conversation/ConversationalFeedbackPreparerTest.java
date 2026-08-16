package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.agent.handler.FeedbackSupersession;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.practices.feedback.ConversationBriefBody;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackThreadKey;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * What a prepared conversational unit carries, and who decided it should exist.
 *
 * <p>Two selections meet here. When the composition stage wrote for this lane, its moves decide what is
 * raised and the unit carries the move. When it did not, the severity ranking that shipped before decides
 * and the body stays NULL — which is the documented path rather than a degraded one, since composition is a
 * stage a review may skip and one that may fail.
 */
class ConversationalFeedbackPreparerTest extends BaseUnitTest {

    private static final long WS = 1L;
    private static final long ALICE = 7L;
    private static final long BOB = 8L;
    private static final String TESTS = "ships-tests-with-the-change";
    private static final String SIZE = "keeps-the-change-reviewable";

    private static final String OPENER = "At what point do you decide the test for a new branch is done?";
    private static final String EVIDENCE = "On !18, !20 and !22 the test arrived a push later.";
    private static final String TARGET = "They name a check they could run before pushing.";

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final FeedbackObservationRepository feedbackObservationRepository = mock(
        FeedbackObservationRepository.class
    );
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OutboundEgressGuard egressGuard = mock(OutboundEgressGuard.class);

    private final ObservationRepository observationRepository = mock(ObservationRepository.class);
    private final FeedbackSupersession supersession = mock(FeedbackSupersession.class);

    private final ConversationalFeedbackPreparer preparer = new ConversationalFeedbackPreparer(
        feedbackRepository,
        feedbackObservationRepository,
        observationRepository,
        supersession,
        eventPublisher,
        egressGuard
    );

    /**
     * The practice behind each locus, as the preparer actually gets it. Projected in production rather than
     * walked off the entity, because the observations reach the preparer from whichever caller routed them
     * and may be detached — so the stub answers from the ids it is asked about rather than with a fixed row,
     * and a mis-keyed lookup cannot pass as a working one.
     */
    private final Map<UUID, String> practiceSlugByObservation = new HashMap<>();

    @BeforeEach
    void deliveryIsAllowedAndNothingIsPreparedYet() {
        lenient().when(egressGuard.deliveryAllowed(any())).thenReturn(true);
        // Nothing queued on any thread unless a case says so, which is what a first run about a habit sees.
        lenient()
            .when(supersession.supersede(anyLong(), anyLong(), any(), any()))
            .thenReturn(FeedbackSupersession.Outcome.standalone());
        lenient().when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(false);
        lenient()
            .when(feedbackRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
            .when(observationRepository.practiceSlugsFor(any()))
            .thenAnswer(invocation -> {
                Collection<UUID> ids = invocation.getArgument(0);
                List<ObservationRepository.ObservationPracticeSlug> rows = new ArrayList<>();
                for (UUID id : ids) {
                    String slug = practiceSlugByObservation.get(id);
                    if (slug != null) {
                        rows.add(new StubPracticeSlug(id, slug));
                    }
                }
                return rows;
            });
    }

    private record StubPracticeSlug(
        UUID observationId,
        String practiceSlug
    ) implements ObservationRepository.ObservationPracticeSlug {
        @Override
        public UUID getObservationId() {
            return observationId;
        }

        @Override
        public String getPracticeSlug() {
            return practiceSlug;
        }
    }

    @Test
    @DisplayName("the composer's move reaches the row in its three parts, and none of them is a script")
    void carriesTheMove() {
        Observation observation = problem(ALICE, TESTS, Severity.MAJOR, 0.9f);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(observation), List.of(move(TESTS)));

        assertThat(prepared).isEqualTo(1);
        Feedback unit = saved().getFirst();
        assertThat(unit.getChannel()).isEqualTo(FeedbackChannel.IN_CHAT);
        assertThat(unit.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
        assertThat(unit.getSuppressionReason()).isNull();
        ConversationBriefBody.Brief brief = ConversationBriefBody.parse(unit.getBody());
        assertThat(brief).isNotNull();
        assertThat(brief.opener()).isEqualTo(OPENER);
        assertThat(brief.evidence()).isEqualTo(EVIDENCE);
        assertThat(brief.target()).isEqualTo(TARGET);
    }

    /**
     * The whole point of the second phase: what is raised is the composer's judgement, not a re-ranking of
     * the measurements underneath it. A locus it read and wrote nothing about is not raised, and owes no row
     * — the reason belongs to the evidence rather than to any gate of ours.
     */
    @Test
    @DisplayName("composed moves select, and outrank the severity order")
    void composedMovesDriveSelection() {
        // The ranking would put the CRITICAL locus first; the composer wrote about the other one.
        Observation loud = problem(ALICE, SIZE, Severity.CRITICAL, 0.99f);
        Observation quiet = problem(ALICE, TESTS, Severity.MINOR, 0.4f);

        int prepared = preparer.prepare(UUID.randomUUID(), WS, List.of(loud, quiet), List.of(move(TESTS)));

        assertThat(prepared).isEqualTo(1);
        assertThat(saved())
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
                assertThat(ConversationBriefBody.parse(unit.getBody())).isNotNull();
            });
    }

    /**
     * Several loci of one practice are one thing to raise, and the composer already named the others inside
     * the move it wrote. The extras take no row and, deliberately, no slot of the per-recipient cap.
     */
    @Test
    @DisplayName("one move per practice per recipient, however many loci it has")
    void collapsesLociOfOnePractice() {
        List<Observation> admitted = List.of(
            problem(ALICE, TESTS, Severity.MAJOR, 0.9f),
            problem(ALICE, TESTS, Severity.MAJOR, 0.8f),
            problem(BOB, TESTS, Severity.MAJOR, 0.7f)
        );

        int prepared = preparer.prepare(UUID.randomUUID(), WS, admitted, List.of(move(TESTS)));

        assertThat(prepared).isEqualTo(2);
        assertThat(saved()).extracting(Feedback::getRecipientUserId).containsExactlyInAnyOrder(ALICE, BOB);
    }

    /**
     * A move about a practice this run measured nothing admissible for cannot be prepared: the mentor's queue
     * is read through the observations bound to a unit, so an evidence-free unit would be invisible there and
     * unexplainable afterwards.
     */
    @Test
    @DisplayName("a move with no admitted locus behind it prepares nothing")
    void dropsAMoveWithNoEvidence() {
        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, SIZE, Severity.MAJOR, 0.9f)),
            List.of(move(TESTS))
        );

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
    }

    /** Our cap, not the evidence's — so the locus still gets a row, and never a move it can never raise. */
    @Test
    @DisplayName("past the per-recipient cap the locus is withheld with a row and no move")
    void capsAtThreePerRecipient() {
        List<Observation> admitted = List.of(
            problem(ALICE, "p-one", Severity.CRITICAL, 0.9f),
            problem(ALICE, "p-two", Severity.MAJOR, 0.9f),
            problem(ALICE, "p-three", Severity.MAJOR, 0.8f),
            problem(ALICE, "p-four", Severity.MINOR, 0.7f)
        );
        List<ComposedFeedbackUnit> composed = List.of(move("p-one"), move("p-two"), move("p-three"), move("p-four"));

        int prepared = preparer.prepare(UUID.randomUUID(), WS, admitted, composed);

        assertThat(prepared).isEqualTo(ConversationalFeedbackPreparer.TOP_N_PER_RECIPIENT);
        assertThat(saved())
            .filteredOn(unit -> unit.getDeliveryState() == FeedbackDeliveryState.SUPPRESSED)
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getSuppressionReason()).isEqualTo(FeedbackSuppressionReason.VOLUME_CAPPED);
                assertThat(unit.getBody()).isNull();
            });
    }

    /**
     * The key is what makes this lane supersedable at all: it is staged to the composer as the handle for
     * "replace this", and the staging drops any row whose key is blank. A raised row therefore always
     * carries one, and a capped row never does — it was not raised, so it is not on the thread, and putting
     * it at the head would leave the queued move behind it unreplaceable.
     */
    @Test
    @DisplayName("every raised row carries its habit's thread key, and a capped row carries none")
    void raisedRowsCarryTheHabitThread() {
        List<Observation> admitted = List.of(
            problem(ALICE, "p-one", Severity.CRITICAL, 0.9f),
            problem(ALICE, "p-two", Severity.MAJOR, 0.9f),
            problem(ALICE, "p-three", Severity.MAJOR, 0.8f),
            problem(ALICE, "p-four", Severity.MINOR, 0.7f)
        );
        List<ComposedFeedbackUnit> composed = List.of(move("p-one"), move("p-two"), move("p-three"), move("p-four"));

        preparer.prepare(UUID.randomUUID(), WS, admitted, composed);

        assertThat(saved())
            .filteredOn(unit -> unit.getDeliveryState() == FeedbackDeliveryState.PREPARED)
            .allSatisfy(unit -> assertThat(unit.getThreadKey()).isNotBlank())
            .extracting(Feedback::getThreadKey)
            .containsExactlyInAnyOrder(
                threadKeyFor("p-one", ALICE),
                threadKeyFor("p-two", ALICE),
                threadKeyFor("p-three", ALICE)
            );
        assertThat(saved())
            .filteredOn(unit -> unit.getDeliveryState() == FeedbackDeliveryState.SUPPRESSED)
            .singleElement()
            .extracting(Feedback::getThreadKey)
            .isNull();
    }

    /** The fallback ranking writes no body, but it still writes a thread a later move can replace. */
    @Test
    @DisplayName("the fallback ranking's rows are supersedable too")
    void fallbackRowsCarryTheHabitThread() {
        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of()
        );

        assertThat(prepared).isEqualTo(1);
        assertThat(saved()).singleElement().extracting(Feedback::getThreadKey).isEqualTo(threadKeyFor(TESTS, ALICE));
    }

    @Test
    @DisplayName("a SUPERSEDE that wins the claim retires the queued move and points back at it")
    void supersedeRetiresTheQueuedMove() {
        UUID retired = UUID.randomUUID();
        String threadKey = threadKeyFor(TESTS, ALICE);
        when(supersession.supersede(WS, ALICE, FeedbackChannel.IN_CHAT, threadKey)).thenReturn(
            new FeedbackSupersession.Outcome(FeedbackSupersession.Disposition.SUPERSEDED, retired)
        );

        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(supersede(TESTS, threadKey))
        );

        assertThat(prepared).isEqualTo(1);
        assertThat(saved())
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getThreadKey()).isEqualTo(threadKey);
                assertThat(unit.getReplacesId()).isEqualTo(retired);
                assertThat(unit.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
            });
    }

    /**
     * Nothing that has been received may be un-said: the mentor already raised the queued move, so it keeps
     * its state and the new move is written beside it. Losing the claim is not a reason to stay silent about
     * something that was measured — the thread continues instead.
     */
    @Test
    @DisplayName("a SUPERSEDE that loses to a raised move is still written, following it")
    void supersedeLosingToADeliveredMoveStillWrites() {
        UUID alreadyRaised = UUID.randomUUID();
        String threadKey = threadKeyFor(TESTS, ALICE);
        when(supersession.supersede(WS, ALICE, FeedbackChannel.IN_CHAT, threadKey)).thenReturn(
            new FeedbackSupersession.Outcome(FeedbackSupersession.Disposition.CONTINUED, alreadyRaised)
        );

        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(supersede(TESTS, threadKey))
        );

        assertThat(prepared).isEqualTo(1);
        assertThat(saved())
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
                assertThat(unit.getReplacesId()).isEqualTo(alreadyRaised);
            });
    }

    /**
     * The runner refuses a key that was never staged, so the composer cannot invent one — but it can name a
     * real key belonging to another of this person's habits. Acting on that would retire a move about
     * something else and leave it unsaid forever, so the move is written as a new one instead.
     */
    @Test
    @DisplayName("a key belonging to another habit claims nothing and is written as new")
    void refusesACrossHabitSupersession() {
        String someoneElsesHabit = threadKeyFor(SIZE, ALICE);

        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(supersede(TESTS, someoneElsesHabit))
        );

        assertThat(prepared).isEqualTo(1);
        verify(supersession, never()).supersede(anyLong(), anyLong(), any(), any());
        assertThat(saved())
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getThreadKey()).isEqualTo(threadKeyFor(TESTS, ALICE));
                assertThat(unit.getReplacesId()).isNull();
            });
    }

    /** A locus whose practice cannot be read has no habit to key on, so nothing can be claimed for it. */
    @Test
    @DisplayName("an unresolved practice claims nothing")
    void anUnresolvedPracticeClaimsNothing() {
        preparer.prepare(UUID.randomUUID(), WS, List.of(problem(ALICE, null, Severity.MAJOR, 0.9f)), List.of());

        verify(supersession, never()).supersede(anyLong(), anyLong(), any(), any());
        assertThat(saved()).singleElement().extracting(Feedback::getThreadKey).isNull();
    }

    /**
     * A re-run reaching a unit it already wrote must not claim a second time: the move it would retire is
     * the one this very unit replaced on the first pass.
     */
    @Test
    @DisplayName("a re-run does not claim the thread again")
    void reRunDoesNotSupersedeTwice() {
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(true);

        preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(supersede(TESTS, threadKeyFor(TESTS, ALICE)))
        );

        verify(supersession, never()).supersede(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("a re-run writes nothing and publishes nothing")
    void reRunIsIdempotent() {
        when(feedbackRepository.existsByAgentJobIdAndPosition(any(), anyInt())).thenReturn(true);
        List<Observation> admitted = List.of(
            problem(ALICE, TESTS, Severity.MAJOR, 0.9f),
            problem(ALICE, SIZE, Severity.MINOR, 0.5f)
        );

        int prepared = preparer.prepare(UUID.randomUUID(), WS, admitted, List.of(move(TESTS), move(SIZE)));

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
        // any(Object.class), not any(): binds the publishEvent(Object) overload the record dispatches to.
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /**
     * Ordinals come from the measurements alone, never from what the composer said about them, so the
     * {@code (agent_job_id, position)} grain a re-run recognises does not move when composition does. Here
     * the top-ranked locus is composed for by nobody: it still consumes its position, and the raised unit
     * still lands on the position its own rank gives it.
     */
    @Test
    @DisplayName("positions are derived from the admitted loci, not from what was composed")
    void ordinalsIgnoreComposition() {
        List<Observation> admitted = List.of(
            problem(ALICE, SIZE, Severity.CRITICAL, 0.9f),
            problem(ALICE, TESTS, Severity.MINOR, 0.5f)
        );

        preparer.prepare(UUID.randomUUID(), WS, admitted, List.of(move(TESTS)));

        assertThat(saved())
            .singleElement()
            .extracting(Feedback::getPosition)
            .isEqualTo(FeedbackLedgerRecorder.IN_CHAT_UNIT_ORDINAL_BASE + 1);
    }

    /**
     * "Say nothing" is a decision, and it is the composer's, so nothing is raised for that practice. No
     * SUPPRESSED row is written either: every {@code WithholdReason} is a property of the evidence or of what
     * the person has already been told, and none of them is a reason this server held something back. Mapping
     * one onto {@code FeedbackSuppressionReason} would mean widening a database CHECK constraint to record a
     * refusal its own vocabulary does not describe.
     */
    @Test
    @DisplayName("a withheld practice is not raised and writes no row")
    void withholdWritesNothing() {
        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(withhold(TESTS))
        );

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /**
     * A turn that only withheld still spoke. Falling back to the severity ranking here would overrule the
     * composer with exactly the mechanism the second phase exists to replace.
     */
    @Test
    @DisplayName("a withhold-only turn does not fall back to the severity ranking")
    void withholdIsNotSilence() {
        List<Observation> admitted = List.of(
            problem(ALICE, TESTS, Severity.MAJOR, 0.9f),
            problem(ALICE, SIZE, Severity.CRITICAL, 0.95f)
        );

        int prepared = preparer.prepare(UUID.randomUUID(), WS, admitted, List.of(withhold(TESTS)));

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("with nothing composed, the severity ranking selects and the body stays NULL")
    void fallsBackToTheRanking() {
        List<Observation> admitted = List.of(
            problem(ALICE, TESTS, Severity.MINOR, 0.4f),
            problem(ALICE, SIZE, Severity.CRITICAL, 0.99f)
        );

        int prepared = preparer.prepare(UUID.randomUUID(), WS, admitted, List.of());

        assertThat(prepared).isEqualTo(2);
        assertThat(saved()).allSatisfy(unit -> assertThat(unit.getBody()).isNull());
        assertThat(saved())
            .extracting(Feedback::getPosition)
            .containsExactly(
                FeedbackLedgerRecorder.IN_CHAT_UNIT_ORDINAL_BASE,
                FeedbackLedgerRecorder.IN_CHAT_UNIT_ORDINAL_BASE + 1
            );
    }

    /** A locus with no readable practice cannot be joined to any move; the ranking still handles it. */
    @Test
    @DisplayName("the fallback does not need a practice slug")
    void fallbackToleratesAnUnresolvedPractice() {
        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, null, Severity.MAJOR, 0.9f)),
            List.of()
        );

        assertThat(prepared).isEqualTo(1);
        assertThat(saved()).singleElement().extracting(Feedback::getBody).isNull();
    }

    @Test
    @DisplayName("silent mode still creates no future work, composed or not")
    void silentModePreparesNothing() {
        when(egressGuard.deliveryAllowed(any())).thenReturn(false);

        int prepared = preparer.prepare(
            UUID.randomUUID(),
            WS,
            List.of(problem(ALICE, TESTS, Severity.MAJOR, 0.9f)),
            List.of(move(TESTS))
        );

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
    }

    private List<Feedback> saved() {
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private static String threadKeyFor(String practiceSlug, long recipient) {
        return FeedbackThreadKey.forPractice(practiceSlug, recipient, FeedbackChannel.IN_CHAT);
    }

    private static ComposedFeedbackUnit move(String practiceSlug) {
        return move(practiceSlug, ComposedFeedbackUnit.Action.NEW, null);
    }

    /** A move the composer wrote to replace the one queued on {@code supersedesThreadKey}. */
    private static ComposedFeedbackUnit supersede(String practiceSlug, String supersedesThreadKey) {
        return move(practiceSlug, ComposedFeedbackUnit.Action.SUPERSEDE, supersedesThreadKey);
    }

    private static ComposedFeedbackUnit move(
        String practiceSlug,
        ComposedFeedbackUnit.Action action,
        String supersedesThreadKey
    ) {
        return new ComposedFeedbackUnit(
            FeedbackChannel.IN_CHAT,
            practiceSlug,
            List.of("obs-0"),
            action,
            supersedesThreadKey,
            null,
            "The test arrives after the review, not with the change",
            null,
            null,
            new ComposedFeedbackUnit.ConversationBrief(OPENER, EVIDENCE, TARGET),
            null
        );
    }

    private static ComposedFeedbackUnit withhold(String practiceSlug) {
        return new ComposedFeedbackUnit(
            FeedbackChannel.IN_CHAT,
            practiceSlug,
            List.of("obs-0"),
            ComposedFeedbackUnit.Action.WITHHOLD,
            null,
            ComposedFeedbackUnit.WithholdReason.ALREADY_SAID,
            null,
            null,
            null,
            null,
            null
        );
    }

    private Observation problem(long about, String practiceSlug, Severity severity, float confidence) {
        Observation observation = mock(Observation.class);
        UUID id = UUID.randomUUID();
        lenient().when(observation.getId()).thenReturn(id);
        lenient().when(observation.getPresence()).thenReturn(Presence.ABSENT);
        lenient().when(observation.getAssessment()).thenReturn(Assessment.BAD);
        lenient().when(observation.getSeverity()).thenReturn(severity);
        lenient().when(observation.getConfidence()).thenReturn(confidence);
        lenient().when(observation.getArtifactKind()).thenReturn(ArtifactKinds.PULL_REQUEST);
        lenient().when(observation.getArtifactId()).thenReturn(100L);
        lenient().when(observation.getAboutUserId()).thenReturn(about);
        lenient().when(observation.getOrigin()).thenReturn(ObservationOrigin.LIVE);
        if (practiceSlug != null) {
            practiceSlugByObservation.put(id, practiceSlug);
        }
        return observation;
    }
}

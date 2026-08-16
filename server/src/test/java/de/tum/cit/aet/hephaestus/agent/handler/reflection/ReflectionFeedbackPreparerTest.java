package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** What actually gets written, and — as importantly — what does not. */
class ReflectionFeedbackPreparerTest extends BaseUnitTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final long WORKSPACE_ID = 7L;
    private static final int BASE = FeedbackLedgerRecorder.REFLECTION_UNIT_ORDINAL_BASE;

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final FeedbackObservationRepository feedbackObservationRepository = mock(
        FeedbackObservationRepository.class
    );
    private final ReflectionFeedbackPreparer preparer = new ReflectionFeedbackPreparer(
        feedbackRepository,
        feedbackObservationRepository
    );

    @Test
    void writesAnAdmittedMessageAsAPreparedReflectionUnitCarryingItsBody() {
        stubSave();

        int prepared = preparer.prepare(JOB_ID, WORKSPACE_ID, 11L, List.of(admitted("ships-tests")), BASE);

        assertThat(prepared).isEqualTo(1);
        Feedback written = captureSaved().getFirst();
        assertThat(written.getChannel()).isEqualTo(FeedbackChannel.REFLECTION);
        assertThat(written.getDeliveryState()).isEqualTo(FeedbackDeliveryState.PREPARED);
        assertThat(written.getRecipientUserId()).isEqualTo(11L);
        assertThat(written.getAboutUserId()).isEqualTo(11L);
        assertThat(written.getPosition()).isEqualTo(BASE);
        // Carries its words at write time — there is no later actor on this lane to compose them.
        assertThat(written.getBody()).contains("### A pattern").contains("**Try next:** Do the thing");
        // Unanchored: the message is about several pieces of work, so naming one would misdescribe it.
        assertThat(written.getArtifactId()).isNull();
    }

    /**
     * The bug this test exists for: one job files observations against several people, and
     * {@code (agent_job_id, position)} is unique. A base fixed for the whole job would make the second
     * recipient's first unit collide with the first recipient's, and the idempotency guard would read
     * that collision as "already written" and drop the row without a word.
     */
    @Test
    void givesEachRecipientOrdinalsThatCannotCollideWithAnothers() {
        stubSave();

        preparer.prepare(JOB_ID, WORKSPACE_ID, 11L, List.of(admitted("a"), admitted("b")), BASE);
        preparer.prepare(JOB_ID, WORKSPACE_ID, 22L, List.of(admitted("a"), admitted("b")), BASE + 2);

        assertThat(captureSaved())
            .extracting(Feedback::getPosition)
            .containsExactly(BASE, BASE + 1, BASE + 2, BASE + 3);
    }

    /**
     * A refusal that is a property of the evidence is not a withholding to explain — no message was ever
     * owed, so no row is written and no ordinal is consumed by a body that does not exist.
     */
    @Test
    void writesNothingForAMessageTheRouterRefused() {
        int prepared = preparer.prepare(
            JOB_ID,
            WORKSPACE_ID,
            11L,
            List.of(
                new ReflectionFeedbackPreparer.RoutedMessage(
                    message("x"),
                    ReflectionRoutingDecision.UNCORROBORATED,
                    List.of()
                )
            ),
            BASE
        );

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void isIdempotentOnARerunOfTheSameJob() {
        when(feedbackRepository.existsByAgentJobIdAndPosition(eq(JOB_ID), anyInt())).thenReturn(true);

        int prepared = preparer.prepare(JOB_ID, WORKSPACE_ID, 11L, List.of(admitted("ships-tests")), BASE);

        assertThat(prepared).isZero();
        verify(feedbackRepository, never()).save(any());
    }

    /** Writing past the band would address another band's rows; fail loudly instead of silently. */
    @Test
    void refusesToWritePastItsOrdinalBand() {
        int lastSlot = BASE + FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH - 1;

        assertThatThrownBy(() ->
            preparer.prepare(JOB_ID, WORKSPACE_ID, 11L, List.of(admitted("a"), admitted("b")), lastSlot)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exceed the ordinal band");
    }

    @Test
    void bindsEveryPieceOfEvidenceToTheUnitItStandsOn() {
        stubSave();
        Observation first = Observation.builder().id(UUID.randomUUID()).build();
        Observation second = Observation.builder().id(UUID.randomUUID()).build();

        preparer.prepare(
            JOB_ID,
            WORKSPACE_ID,
            11L,
            List.of(
                new ReflectionFeedbackPreparer.RoutedMessage(
                    message("ships-tests"),
                    ReflectionRoutingDecision.ADMIT,
                    List.of(first, second)
                )
            ),
            BASE
        );

        verify(feedbackObservationRepository, times(2)).insertIfAbsent(any(), any(), eq("PRIMARY"), anyInt());
    }

    private void stubSave() {
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private List<Feedback> captureSaved() {
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static ReflectionFeedbackPreparer.RoutedMessage admitted(String practiceSlug) {
        return new ReflectionFeedbackPreparer.RoutedMessage(
            message(practiceSlug),
            ReflectionRoutingDecision.ADMIT,
            List.of()
        );
    }

    private static ComposedReflectionMessage message(String practiceSlug) {
        return new ComposedReflectionMessage(practiceSlug, "A pattern", "What keeps happening.", "Do the thing");
    }
}

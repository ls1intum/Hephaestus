package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackGate;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSend;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendQueue;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionReason;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@Tag("unit")
class FeedbackDecisionServiceTest {

    final FeedbackRepository feedback = mock(FeedbackRepository.class);
    final FeedbackDecisionRepository decisions = mock(FeedbackDecisionRepository.class);
    final FeedbackBasisRepository basis = mock(FeedbackBasisRepository.class);
    final FeedbackCopyRepository copies = mock(FeedbackCopyRepository.class);
    final FeedbackSendRepository sends = mock(FeedbackSendRepository.class);
    final FeedbackGate gate = mock(FeedbackGate.class);
    final PersonDataWriteFence privacy = mock(PersonDataWriteFence.class);
    final FeedbackContentDigest digests = mock(FeedbackContentDigest.class);
    final Instant now = Instant.EPOCH;
    final UUID id = UUID.randomUUID();
    final FeedbackDecisionService service = new FeedbackDecisionService(
            feedback,
            decisions,
            basis,
            copies,
            sends,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(now, ZoneOffset.UTC),
            gate,
            digests,
            privacy,
            new FeedbackSendQueue(sends, Clock.fixed(now, ZoneOffset.UTC)));
    Feedback row;

    @BeforeEach
    void setup() {
        when(privacy.holdForAccountWrite(anyLong())).thenReturn(true);
        when(digests.current(1, id)).thenReturn(digest());
        row = Feedback.builder()
                .id(id)
                .workspaceId(1)
                .reviewId(UUID.randomUUID())
                .developerId(2)
                .destination(FeedbackDestination.ON_WORK)
                .state(FeedbackState.AWAITING_APPROVAL)
                .body("Add a regression test.")
                .build();
        when(feedback.lockByIdAndWorkspaceId(id, 1)).thenReturn(Optional.of(row));
        when(decisions.save(any())).thenAnswer(a -> a.getArgument(0));
        when(gate.evaluate(row, FeedbackCheckStage.APPROVED, false))
                .thenReturn(new FeedbackCheck(
                        FeedbackCheckStage.APPROVED,
                        true,
                        null,
                        now,
                        new FeedbackCheckFacts(0, 0, true, true, true, true, false, true, true, false)));
    }

    @Test
    void shouldRefuseNewDecisionTextDuringActorErasure() {
        when(privacy.holdForAccountWrite(3)).thenReturn(false);
        assertThatThrownBy(
                        () -> service.decide(1, id, 3, FeedbackDecisionAction.WITHDRAW, null, "Withdraw this.", null))
                .isInstanceOf(ResponseStatusException.class);
        verify(decisions, never()).save(any());
    }

    String digest() {
        return "a".repeat(64);
    }

    @Test
    void shouldAppendApprovalAndQueueTheSamePreparedPiece() {
        var decision = service.decide(1, id, 3, FeedbackDecisionAction.APPROVE, null, null, digest());
        assertThat(decision.getAction()).isEqualTo(FeedbackDecisionAction.APPROVE);
        assertThat(decision.getFeedbackId()).isEqualTo(id);
        var capture = ArgumentCaptor.forClass(FeedbackSend.class);
        verify(sends).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getState()).isEqualTo(FeedbackSendState.PENDING);
        assertThat(capture.getValue().getAction()).isEqualTo(FeedbackSendAction.POST);
    }

    @Test
    void shouldRefuseChangedContentAndAnyExistingCopyFence() {
        assertThatThrownBy(() -> service.decide(1, id, 3, FeedbackDecisionAction.APPROVE, null, null, "0".repeat(64)))
                .isInstanceOf(ResponseStatusException.class);
        when(copies.hasWriteFence(1, id)).thenReturn(true);
        assertThatThrownBy(() -> service.decide(
                        1, id, 3, FeedbackDecisionAction.REJECT, FeedbackDecisionReason.INCORRECT, null, digest()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldQueueCorrectionAgainstExistingReceiptsAfterWithdrawal() {
        when(copies.lockByFeedbackId(1, id))
                .thenReturn(List.of(FeedbackCopy.builder()
                        .id(UUID.randomUUID())
                        .state(FeedbackCopyState.POSTED)
                        .build()));
        var decision = service.decide(
                1, id, 3, FeedbackDecisionAction.WITHDRAW, FeedbackDecisionReason.OTHER, "Remove this guidance.", null);
        assertThat(decision.getAction()).isEqualTo(FeedbackDecisionAction.WITHDRAW);
        var capture = ArgumentCaptor.forClass(FeedbackSend.class);
        verify(sends).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getAction()).isEqualTo(FeedbackSendAction.RETRACT);
    }

    @Test
    void shouldKeepAnUnsettledPostAheadOfCorrection() {
        when(sends.lockByFeedbackId(1, id))
                .thenReturn(Optional.of(FeedbackSend.builder()
                        .state(FeedbackSendState.UNCERTAIN)
                        .action(FeedbackSendAction.POST)
                        .build()));
        when(copies.lockByFeedbackId(1, id))
                .thenReturn(List.of(
                        FeedbackCopy.builder().state(FeedbackCopyState.POSTED).build()));
        service.decide(1, id, 3, FeedbackDecisionAction.WITHDRAW, null, "Withdraw it.", null);
        verify(sends, never()).save(any());
    }
}

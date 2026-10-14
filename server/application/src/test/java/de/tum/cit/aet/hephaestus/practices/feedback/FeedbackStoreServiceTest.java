package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionService;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackCompositionContextService;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackPreparationService;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.node.JsonNodeFactory;

@Tag("unit")
class FeedbackStoreServiceTest {

    final FeedbackRepository feedback = mock(FeedbackRepository.class);
    final FeedbackBasisRepository basis = mock(FeedbackBasisRepository.class);
    final FeedbackCopyRepository copies = mock(FeedbackCopyRepository.class);
    final FeedbackSendRepository sends = mock(FeedbackSendRepository.class);
    final ResultRepository results = mock(ResultRepository.class);
    final ReviewRepository reviews = mock(ReviewRepository.class);
    final FeedbackGate gate = mock(FeedbackGate.class);
    final PersonDataWriteFence privacy = mock(PersonDataWriteFence.class);
    final FeedbackPreparationService preparation = mock(FeedbackPreparationService.class);
    final FeedbackContentDigest digests = mock(FeedbackContentDigest.class);
    final FeedbackStoreService store = new FeedbackStoreService(
            feedback,
            basis,
            copies,
            sends,
            results,
            mock(ProblemRepository.class),
            reviews,
            mock(FeedbackDecisionService.class),
            mock(FeedbackDecisionRepository.class),
            mock(FeedbackResponseService.class),
            mock(FeedbackVisibilityService.class),
            gate,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            privacy,
            preparation,
            new FeedbackLedgerRecorder(feedback, basis, copies),
            digests,
            mock(FeedbackCompositionContextService.class));
    final UUID reviewId = UUID.randomUUID(), resultId = UUID.randomUUID();
    final Review review = Review.builder()
            .id(reviewId)
            .workspaceId(1)
            .itemId(3)
            .kind(ReviewKind.ITEM)
            .status(ReviewStatus.RUNNING)
            .build();
    FeedbackDraft draft;

    @BeforeEach
    void setup() {
        when(preparation.prepare(any(), any()))
                .thenAnswer(call -> new FeedbackPreparationService.Preparation(call.getArgument(0), null));
        UUID id = FeedbackIds.onWork(1, reviewId, 2), copyId = FeedbackIds.copy(id, FeedbackCopyKind.SUMMARY, 0);
        draft = new FeedbackDraft(
                id,
                1,
                reviewId,
                3L,
                2,
                FeedbackDestination.ON_WORK,
                FeedbackState.READY,
                null,
                "Add a focused regression test.",
                List.of(new FeedbackBasisInput(resultId, null, 0)),
                List.of(new FeedbackCopyInput(
                        copyId,
                        FeedbackCopyKind.SUMMARY,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        copyId.toString(),
                        FeedbackIds.writeMarker(copyId))),
                Map.of());
        when(reviews.lockByIdAndWorkspaceId(reviewId, 1)).thenReturn(Optional.of(review));
        when(results.findByWorkspaceIdAndIdIn(1, List.of(resultId)))
                .thenReturn(List.of(Result.builder()
                        .id(resultId)
                        .developerId(2L)
                        .workspaceId(1)
                        .reviewId(reviewId)
                        .build()));
        when(privacy.holdForAccountWrite(2)).thenReturn(true);
        when(digests.current(1, draft.id())).thenReturn("a".repeat(64));
        when(digests.orderedPackageDigest(List.of("a".repeat(64)))).thenReturn("b".repeat(64));
        when(feedback.saveAndFlush(any())).thenAnswer(call -> {
            Feedback row = call.getArgument(0);
            when(feedback.findByIdAndWorkspaceId(row.getId(), row.getWorkspaceId()))
                    .thenReturn(Optional.of(row));
            return row;
        });
        when(gate.evaluate(any(), any(), anyBoolean()))
                .thenReturn(new FeedbackCheck(
                        FeedbackCheckStage.EGRESS,
                        true,
                        null,
                        Instant.EPOCH,
                        new FeedbackCheckFacts(0, 0, true, true, true, true, false, true, true, false)));
    }

    @Test
    void shouldRetainPublicCompletionAndOtherChecksWhenRecordingPreparation() {
        var publicAt = Instant.EPOCH.minusSeconds(60);
        review.setPublicReviewAt(publicAt);
        review.setChecks(JsonNodeFactory.instance.objectNode().put("cancellationRequested", false));
        var receipt = store.prepareAll(1, reviewId, List.of(draft));
        assertThat(receipt.preparedAt()).isEqualTo(Instant.EPOCH);
        assertThat(review.getPublicReviewAt()).isEqualTo(publicAt);
        assertThat(review.getChecks().path("cancellationRequested").asBoolean()).isFalse();
        assertThat(review.getChecks()
                        .path("feedbackPreparationReceipts")
                        .path(receipt.packageDigest())
                        .asString())
                .isEqualTo(receipt.preparedAt().toString());
    }

    @Test
    void shouldPersistBodyBasisAndCopyIdentityBeforeQueuingAnySend() {
        var receipt = store.prepareAll(1, reviewId, List.of(draft));
        assertThat(receipt.pieces().getFirst().feedbackId()).isEqualTo(draft.id());
        assertThat(receipt.packageDigest()).isEqualTo("b".repeat(64));
        assertThat(receipt.preparedAt()).isEqualTo(Instant.EPOCH);
        var order = inOrder(feedback, basis, copies, sends);
        order.verify(feedback).saveAndFlush(any(Feedback.class));
        order.verify(basis).saveAll(anyList());
        order.verify(copies).saveAll(anyList());
        order.verify(basis).flush();
        order.verify(copies).flush();
        order.verify(sends).saveAndFlush(any(FeedbackSend.class));
        var capture = ArgumentCaptor.forClass(FeedbackSend.class);
        verify(sends).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getAction()).isEqualTo(FeedbackSendAction.POST);
        assertThat(capture.getValue().getFeedbackId()).isEqualTo(draft.id());
    }

    @Test
    void shouldPrepareApprovalWithoutAProviderSend() {
        when(gate.requiresApproval(any())).thenReturn(true);
        store.prepareAll(1, reviewId, List.of(draft));
        verify(feedback).updateDelivery(1, draft.id(), FeedbackState.AWAITING_APPROVAL, null, null);
        verify(sends, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseCrossDeveloperBasisBeforePersistingAnything() {
        when(results.findByWorkspaceIdAndIdIn(1, List.of(resultId)))
                .thenReturn(
                        List.of(Result.builder().id(resultId).developerId(9L).build()));
        assertThatThrownBy(() -> store.prepareAll(1, reviewId, List.of(draft)))
                .isInstanceOf(ResponseStatusException.class);
        verify(feedback, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseASummaryWithNoMatchingPhysicalCopy() {
        var missing = new FeedbackDraft(
                draft.id(),
                draft.workspaceId(),
                draft.reviewId(),
                draft.itemId(),
                draft.developerId(),
                draft.destination(),
                draft.state(),
                null,
                draft.body(),
                draft.basis(),
                List.of(),
                Map.of());
        assertThatThrownBy(() -> store.prepareAll(1, reviewId, List.of(missing)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(feedback, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseScansBeforeAnyProjection() {
        when(reviews.lockByIdAndWorkspaceId(reviewId, 1))
                .thenReturn(Optional.of(Review.builder().kind(ReviewKind.SCAN).build()));
        assertThatThrownBy(() -> store.prepareAll(1, reviewId, List.of(draft)))
                .isInstanceOf(ResponseStatusException.class);
        verify(feedback, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefusePreparationDuringErasure() {
        when(privacy.holdForAccountWrite(2)).thenReturn(false);
        assertThatThrownBy(() -> store.prepareAll(1, reviewId, List.of(draft)))
                .isInstanceOf(ResponseStatusException.class);
        verify(feedback, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectAnUnknownWriteAsAnAdministrativeRetry() {
        var row = Feedback.builder()
                .id(draft.id())
                .workspaceId(1)
                .destination(FeedbackDestination.ON_WORK)
                .state(FeedbackState.FAILED)
                .build();
        when(feedback.lockByIdAndWorkspaceId(draft.id(), 1)).thenReturn(Optional.of(row));
        when(copies.lockByFeedbackId(1, draft.id()))
                .thenReturn(List.of(FeedbackCopy.builder()
                        .state(FeedbackCopyState.WRITE_STARTED)
                        .writeStartedAt(Instant.EPOCH)
                        .build()));
        assertThatThrownBy(() -> store.retry(1, draft.id(), 4)).isInstanceOf(ResponseStatusException.class);
        verify(copies, never()).retryAbsent(anyLong(), any());
    }

    @Test
    void shouldPreserveAConfirmedSiblingWhenWithholdingTheRemainingPlannedCopies() {
        var row = Feedback.builder().id(draft.id()).workspaceId(1).build();
        when(copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(1, draft.id()))
                .thenReturn(List.of(FeedbackCopy.builder()
                        .state(FeedbackCopyState.POSTED)
                        .postedAt(Instant.EPOCH)
                        .build()));
        store.withhold(row, FeedbackStopReason.NO_AI);
        verify(feedback).updateDelivery(1, draft.id(), FeedbackState.DELIVERED, null, Instant.EPOCH);
    }

    @Test
    void shouldRetainASiblingsNewReceiptWhenWithholdingAnUnknownRemainder() {
        var row = Feedback.builder().id(draft.id()).workspaceId(1).build();
        when(copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(1, draft.id()))
                .thenReturn(List.of(
                        FeedbackCopy.builder()
                                .state(FeedbackCopyState.POSTED)
                                .postedAt(Instant.EPOCH)
                                .build(),
                        FeedbackCopy.builder()
                                .state(FeedbackCopyState.WRITE_STARTED)
                                .writeStartedAt(Instant.EPOCH)
                                .build()));
        store.withhold(row, FeedbackStopReason.NO_AI);
        verify(feedback)
                .updateDelivery(
                        1,
                        draft.id(),
                        FeedbackState.UNCONFIRMED,
                        FeedbackStopReason.DELIVERY_UNCONFIRMED,
                        Instant.EPOCH);
    }

    @Test
    void shouldKeepAProvedProviderFailureWhenTheUnrequestedRemainderIsWithheld() {
        var row = Feedback.builder().id(draft.id()).workspaceId(1).build();
        when(copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(1, draft.id()))
                .thenReturn(List.of(FeedbackCopy.builder()
                        .state(FeedbackCopyState.FAILED)
                        .lastError("ABSENT: exact readback")
                        .build()));
        store.withhold(row, FeedbackStopReason.NO_AI);
        verify(feedback).updateDelivery(1, draft.id(), FeedbackState.FAILED, null, null);
    }

    @Test
    void shouldQueueAnExplicitRetryOnlyAfterProvedAbsenceAndFreshPolicy() {
        var row = Feedback.builder()
                .id(draft.id())
                .workspaceId(1)
                .destination(FeedbackDestination.ON_WORK)
                .state(FeedbackState.FAILED)
                .build();
        when(feedback.lockByIdAndWorkspaceId(draft.id(), 1)).thenReturn(Optional.of(row));
        when(copies.lockByFeedbackId(1, draft.id()))
                .thenReturn(List.of(FeedbackCopy.builder()
                        .state(FeedbackCopyState.FAILED)
                        .writeStartedAt(Instant.EPOCH)
                        .lastError("ABSENT: exact readback")
                        .build()));
        when(gate.evaluate(row, FeedbackCheckStage.EGRESS, true))
                .thenReturn(new FeedbackCheck(
                        FeedbackCheckStage.EGRESS,
                        true,
                        null,
                        Instant.EPOCH,
                        new FeedbackCheckFacts(0, 0, true, true, true, true, false, true, true, true)));
        when(copies.retryAbsent(1, draft.id())).thenReturn(1);
        assertThat(store.retry(1, draft.id(), 4)).isTrue();
        verify(feedback).updateDelivery(1, draft.id(), FeedbackState.READY, null, null);
        verify(sends).saveAndFlush(any(FeedbackSend.class));
    }
}

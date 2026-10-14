package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.PreparedFeedback;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackPreparationReceiptIntegrationTest extends AbstractFeedbackDeliveryIntegrationTest {
    @Autowired
    FeedbackContentDigest digests;

    @Autowired
    ReviewRepository reviews;

    @Test
    void shouldReturnOnePersistedPreparationTimeAndCanonicalOrderedPackageDigest() {
        var receipt = store.prepareAll(workspace.getId(), reviewId, List.of(draft(true), conversation(developerId)));
        var pieceDigests =
                receipt.pieces().stream().map(PreparedFeedback::packageDigest).toList();
        assertThat(receipt.pieces()).hasSize(2);
        assertThat(receipt.pieces())
                .allSatisfy(piece -> assertThat(piece.preparedAt()).isEqualTo(receipt.preparedAt()));
        assertThat(receipt.preparedAt()).isEqualTo(current().getCreatedAt());
        assertThat(receipt.packageDigest()).isEqualTo(digests.orderedPackageDigest(pieceDigests));
        assertThat(receipt.pieces().getFirst().packageDigest())
                .isEqualTo(digests.current(workspace.getId(), feedbackId));
        var reversed = store.prepareAll(workspace.getId(), reviewId, List.of(conversation(developerId), draft(true)));
        assertThat(reversed.packageDigest()).isNotEqualTo(receipt.packageDigest());
        assertThat(reversed.preparedAt()).isAfterOrEqualTo(receipt.preparedAt());
        assertThat(reversed.pieces())
                .allSatisfy(piece -> assertThat(piece.preparedAt()).isEqualTo(reversed.preparedAt()));
        assertThat(store.prepareAll(workspace.getId(), reviewId, List.of(conversation(developerId), draft(true))))
                .isEqualTo(reversed);
        assertThat(reviews.findById(reviewId).orElseThrow().getPublicReviewAt()).isNull();
        assertThat(provider.summaryCreates).isZero();
        assertThat(provider.noteCreates).isZero();
    }

    @Test
    void shouldReturnTheSameRecordedReceiptOnReplayWithoutAnotherCopyOrSend() {
        var first = store.prepareAll(workspace.getId(), reviewId, List.of(draft(false)));
        var second = store.prepareAll(workspace.getId(), reviewId, List.of(draft(false)));
        assertThat(second).isEqualTo(first);
        assertThat(children()).hasSize(1);
        assertThat(sends.findById(feedbackId).orElseThrow().getAttempts()).isZero();
        assertThat(provider.summaryCreates).isZero();
    }

    @Test
    void shouldRollBackEveryPieceAndPreparationTimeWhenALaterPieceIsInvalid() {
        var invalid = conversation(developerId + 1);
        assertThatThrownBy(() -> store.prepareAll(workspace.getId(), reviewId, List.of(draft(false), invalid)))
                .isInstanceOf(RuntimeException.class);
        assertThat(feedback.findById(feedbackId)).isEmpty();
        assertThat(feedback.findById(invalid.id())).isEmpty();
        assertThat(sends.findById(feedbackId)).isEmpty();
        assertThat(children()).isEmpty();
        assertThat(reviews.findByIdAndWorkspaceId(reviewId, workspace.getId())
                        .orElseThrow()
                        .getPublicReviewAt())
                .isNull();
        assertThat(reviews.findById(reviewId)
                        .orElseThrow()
                        .getChecks()
                        .path("feedbackPreparationReceipts")
                        .isMissingNode())
                .isTrue();
        assertThat(provider.summaryCreates).isZero();
    }

    @Test
    void shouldPrepareAnEmptyPackageWithARealRecordedTime() {
        var empty = store.prepareAll(workspace.getId(), reviewId, List.of());
        assertThat(empty.pieces()).isEmpty();
        assertThat(empty.packageDigest()).isEqualTo(digests.orderedPackageDigest(List.of()));
        var review = reviews.findById(reviewId).orElseThrow();
        assertThat(review.getPublicReviewAt()).isNull();
        assertThat(review.getChecks()
                        .path("feedbackPreparationReceipts")
                        .path(empty.packageDigest())
                        .asString())
                .isEqualTo(empty.preparedAt().toString());
        assertThat(store.prepareAll(workspace.getId(), reviewId, List.of())).isEqualTo(empty);
    }

    @Test
    void shouldPreparePrivateFeedbackWithoutClaimingPublicCompletion() {
        var prepared = store.prepareAll(workspace.getId(), reviewId, List.of(conversation(developerId)));
        assertThat(prepared.pieces()).hasSize(1);
        assertThat(reviews.findById(reviewId).orElseThrow().getPublicReviewAt()).isNull();
        assertThat(store.prepareAll(workspace.getId(), reviewId, List.of(conversation(developerId))))
                .isEqualTo(prepared);
    }

    @Test
    void shouldPersistTheExactBasisAndCopyBeforeQueueingThePreparedFeedback() {
        var proposal = draft(true);
        store.prepareAll(workspace.getId(), reviewId, List.of(proposal));
        assertThat(jdbc.queryForList(
                        "SELECT body FROM feedback WHERE workspace_id=? AND id=?",
                        String.class,
                        workspace.getId(),
                        feedbackId))
                .containsExactly("Add a focused regression test.");
        assertThat(jdbc.queryForList(
                        "SELECT result_id FROM feedback_basis WHERE workspace_id=? AND" + " feedback_id=?",
                        UUID.class,
                        workspace.getId(),
                        feedbackId))
                .containsExactly(supportingResult.getId());
        assertThat(children())
                .extracting(copy -> copy.getId())
                .containsExactlyElementsOf(
                        proposal.copies().stream().map(FeedbackCopyInput::id).toList());
        assertThat(jdbc.queryForList(
                        "SELECT action || '|' || state FROM feedback_send WHERE" + " workspace_id=? AND feedback_id=?",
                        String.class,
                        workspace.getId(),
                        feedbackId))
                .containsExactly("POST|PENDING");
    }

    @Test
    void shouldRetainThePublicCompletionAndOtherChecksWhenRecordingPreparation() {
        var publicAt = AT.plusSeconds(60);
        jdbc.update(
                "UPDATE review SET"
                        + " public_review_at=?,checks=jsonb_build_object('cancellationRequested',false)"
                        + " WHERE workspace_id=? AND id=?",
                Timestamp.from(publicAt),
                workspace.getId(),
                reviewId);
        var receipt = store.prepareAll(workspace.getId(), reviewId, List.of(draft(false)));
        var retained = reviews.findById(reviewId).orElseThrow();
        assertThat(retained.getPublicReviewAt()).isEqualTo(publicAt);
        assertThat(retained.getChecks().path("cancellationRequested").asBoolean())
                .isFalse();
        assertThat(retained.getChecks()
                        .path("feedbackPreparationReceipts")
                        .path(receipt.packageDigest())
                        .asString())
                .isEqualTo(receipt.preparedAt().toString());
    }

    @Test
    void shouldRequireApprovalWithoutQueueingAPostWhenTheCurrentPolicyRequiresIt() {
        when(gate.requiresApproval(any())).thenReturn(true);
        store.prepareAll(workspace.getId(), reviewId, List.of(draft(false)));
        assertThat(current().getState()).isEqualTo(FeedbackState.AWAITING_APPROVAL);
        assertThat(children()).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM feedback_send WHERE workspace_id=? AND" + " feedback_id=?",
                        Long.class,
                        workspace.getId(),
                        feedbackId))
                .isZero();
    }

    private FeedbackDraft conversation(long developer) {
        UUID id = FeedbackIds.conversation(workspace.getId(), reviewId, developer, List.of(supportingResult.getId()));
        UUID copyId = FeedbackIds.copy(id, FeedbackCopyKind.CHAT_TURN, 0);
        return new FeedbackDraft(
                id,
                workspace.getId(),
                reviewId,
                itemId,
                developer,
                FeedbackDestination.CONVERSATION,
                FeedbackState.READY,
                null,
                "Discuss a focused regression test.",
                List.of(new FeedbackBasisInput(supportingResult.getId(), null, 0)),
                List.of(new FeedbackCopyInput(
                        copyId, FeedbackCopyKind.CHAT_TURN, 0, null, null, null, null, null, copyId.toString(), null)),
                Map.of());
    }
}

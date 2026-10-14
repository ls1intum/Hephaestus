package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.LookupPresence;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackSendRecovery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FeedbackSendRecoveryIntegrationTest extends AbstractFeedbackDeliveryIntegrationTest {
    @Test
    void shouldRecoverAnExpiredClaimBeforeAnyFenceWithOneCreate() {
        prepare(false);
        var attempt = transactions.claim(workspace.getId(), feedbackId, "crashed-before-fence");
        assertThat(attempt).isNotNull();
        assertThat(children().getFirst().getWriteStartedAt()).isNull();
        expireClaim();
        newSender().send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(current().getState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldReadBackACrashAfterTheFenceBeforeAnyProviderCall() {
        prepare(false);
        String owner = "crashed-after-fence";
        var attempt = transactions.claim(workspace.getId(), feedbackId, owner);
        assertThat(attempt).isNotNull();
        var copyId = children().getFirst().getId();
        assertThat(transactions.fence(workspace.getId(), feedbackId, owner, List.of(copyId), List.of()))
                .isTrue();
        var firstFence = children().getFirst().getWriteStartedAt();
        expireClaim();
        newSender().send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isZero();
        assertThat(current().getState()).isEqualTo(FeedbackState.FAILED);
        assertThat(children().getFirst().getReadback()).isEqualTo(FeedbackCopyReadback.ABSENT);
        store.retry(workspace.getId(), feedbackId, developerId);
        newSender().send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(children().getFirst().getWriteStartedAt()).isEqualTo(firstFence);
    }

    @Test
    void shouldSettleAnUnknownPostBeforeOpeningItsWithdrawalCorrection() {
        prepare(false);
        provider.summaryFailure = Failure.AFTER_CREATE;
        sender.send(workspace.getId(), feedbackId);
        store.decide(
                workspace.getId(),
                feedbackId,
                developerId,
                FeedbackDecisionAction.WITHDRAW,
                null,
                "Withdraw this guidance.",
                null);
        assertThat(sends.findById(feedbackId).orElseThrow().getAction()).isEqualTo(FeedbackSendAction.POST);
        due();
        newSender().send(workspace.getId(), feedbackId);
        assertThat(sends.findById(feedbackId).orElseThrow().getAction()).isEqualTo(FeedbackSendAction.RETRACT);
        newSender().send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(provider.updates).isEqualTo(1);
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.RETRACTED);
        assertThat(current().getDeliveredAt()).isNotNull();
    }

    @Test
    void shouldOnlyReadBackAnUnknownCopyAndWithholdUnrequestedSiblingsDuringRecovery() {
        prepare(true);
        String owner = "lost-package-owner";
        transactions.claim(workspace.getId(), feedbackId, owner);
        var summary = children().getFirst();
        transactions.fence(workspace.getId(), feedbackId, owner, List.of(summary.getId()), List.of());
        provider.physical.put(summary.getId(), current().getBody());
        expireClaim();
        recovery().recover();
        assertThat(provider.summaryCreates).isZero();
        assertThat(provider.noteCreates).isZero();
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.POSTED);
        assertThat(children().get(1).getState()).isEqualTo(FeedbackCopyState.WITHHELD);
        assertThat(children().get(1).getWriteStartedAt()).isNull();
        assertThat(current().getState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldNeverCreateAConfirmedCopyWhenRecoveryRunsAfterMigration() {
        seedHistoricalCopy(true);
        var original = children().getFirst();
        recovery().recover();
        recovery().recover();
        assertThat(provider.summaryCreates).isZero();
        assertThat(children().getFirst().getExternalRef()).isEqualTo(original.getExternalRef());
        assertThat(children().getFirst().getWriteMarker()).isEqualTo("<!-- released-feedback-marker -->");
        assertThat(current().getDeliveredAt()).isEqualTo(AT);
    }

    @Test
    void shouldReconcileAnUnknownCopyBeforeAnyCreate() {
        seedHistoricalCopy(false);
        recovery().recover();
        assertThat(provider.summaryCreates).isZero();
        assertThat(provider.lastSummaryLookupMarker).isEqualTo("<!-- released-feedback-marker -->");
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.POSTED);
        assertThat(children().getFirst().getWriteStartedAt()).isEqualTo(AT);
        assertThat(children().getFirst().getWriteKey()).isEqualTo("released-write-key");
    }

    @Test
    void shouldNotCreateACommentWhenAProviderCorrectionIsRestored() {
        seedHistoricalCopy(true);
        var copy = children().getFirst();
        copies.confirmCorrection(workspace.getId(), feedbackId, copy.getId(), FeedbackCopyState.RETRACTED.name());
        provider.physical.put(copy.getId(), "This feedback was withdrawn.");
        jdbc.update(
                "UPDATE feedback_send SET"
                        + " action='RETRACT',state='PENDING',next_attempt_at=now()-interval '1 second'"
                        + " WHERE workspace_id=? AND feedback_id=?",
                workspace.getId(),
                feedbackId);
        recovery().recover();
        recovery().recover();
        assertThat(provider.summaryCreates).isZero();
        assertThat(provider.updates).isEqualTo(1);
        assertThat(provider.physical.get(copy.getId())).isEqualTo(current().getBody());
        assertThat(children().getFirst().getExternalRef()).isEqualTo(copy.getExternalRef());
        assertThat(children().getFirst().getPostedAt()).isEqualTo(AT);
    }

    private FeedbackSendRecovery recovery() {
        return new FeedbackSendRecovery(sends, newSender(), Clock.systemUTC());
    }

    private void seedHistoricalCopy(boolean confirmed) {
        prepare(false);
        var copy = children().getFirst();
        jdbc.update(
                "UPDATE feedback_copy SET write_key='released-write-key',write_marker='<!--"
                        + " released-feedback-marker"
                        + " -->',write_started_at=?,state=?,external_ref=?,posted_at=? WHERE id=? AND"
                        + " workspace_id=?",
                Timestamp.from(AT),
                confirmed ? "POSTED" : "WRITE_STARTED",
                confirmed ? copy.getId().toString() : null,
                confirmed ? Timestamp.from(AT) : null,
                copy.getId(),
                workspace.getId());
        jdbc.update(
                "UPDATE feedback SET state=?,stop_reason=?,delivered_at=? WHERE id=? AND" + " workspace_id=?",
                confirmed ? "DELIVERED" : "UNCONFIRMED",
                confirmed ? null : "DELIVERY_UNCONFIRMED",
                confirmed ? Timestamp.from(AT) : null,
                feedbackId,
                workspace.getId());
        jdbc.update(
                "UPDATE feedback_send SET state=?,next_attempt_at=now()-interval '1 second' WHERE"
                        + " workspace_id=? AND feedback_id=?",
                confirmed ? "DONE" : "UNCERTAIN",
                workspace.getId(),
                feedbackId);
        provider.physical.put(copy.getId(), current().getBody());
    }

    private void expireClaim() {
        jdbc.update(
                "UPDATE feedback_send SET lease_expires_at=now()-interval '1"
                        + " second',next_attempt_at=now()-interval '1 second' WHERE workspace_id=? AND"
                        + " feedback_id=?",
                workspace.getId(),
                feedbackId);
    }

    @Test
    void shouldRecoverTheOriginalCopyAfterTheProviderCreatedItButItsResponseWasLost() {
        prepare(false);
        provider.summaryFailure = Failure.AFTER_CREATE;
        sender.send(workspace.getId(), feedbackId);
        var fence = children().getFirst().getWriteStartedAt();
        assertThat(current().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(provider.physical).containsKey(children().getFirst().getId());
        due();
        newSender().send(workspace.getId(), feedbackId);
        assertThat(current().getState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(children().getFirst().getWriteStartedAt()).isEqualTo(fence);
    }

    @Test
    void shouldRequireAnExplicitRetryAfterReadbackProvesTheCopyAbsent() {
        prepare(false);
        provider.summaryFailure = Failure.BEFORE_CREATE;
        sender.send(workspace.getId(), feedbackId);
        due();
        newSender().send(workspace.getId(), feedbackId);
        assertThat(current().getState()).isEqualTo(FeedbackState.FAILED);
        assertThat(children().getFirst().getReadback()).isEqualTo(FeedbackCopyReadback.ABSENT);
        assertThat(provider.summaryCreates).isEqualTo(1);
        var fence = children().getFirst().getWriteStartedAt();
        provider.summaryFailure = Failure.NONE;
        store.retry(workspace.getId(), feedbackId, developerId);
        newSender().send(workspace.getId(), feedbackId);
        assertThat(current().getState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(provider.summaryCreates).isEqualTo(2);
        assertThat(children().getFirst().getWriteStartedAt()).isEqualTo(fence);
    }

    @Test
    void shouldKeepUnknownVisibilityAfterTheBoundedReadbackBudgetWithoutAnotherPost() {
        prepare(false);
        provider.summaryFailure = Failure.AFTER_CREATE;
        sender.send(workspace.getId(), feedbackId);
        provider.readback = LookupPresence.UNKNOWN;
        for (int attempt = 0; attempt < 8; attempt++) {
            due();
            newSender().send(workspace.getId(), feedbackId);
        }
        assertThat(current().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.WRITE_STARTED);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(sends.findById(feedbackId).orElseThrow().getState()).isEqualTo(FeedbackSendState.DONE);
        assertThatThrownBy(() -> store.retry(workspace.getId(), feedbackId, developerId))
                .isInstanceOf(ResponseStatusException.class);
    }
}

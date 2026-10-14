package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkChanged;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackMarkRestorationIntegrationTest extends AbstractFeedbackDeliveryIntegrationTest {
    @Autowired
    FeedbackContentDigest digests;

    private UUID mark() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " result_mark(id,workspace_id,result_id,kind,reason,created_by_id,created_at)"
                        + " VALUES (?,?,?,'INCORRECT','Review the basis.',?,now())",
                id,
                workspace.getId(),
                supportingResult.getId(),
                developerId);
        store.marked(new ResultMarkChanged(
                id, workspace.getId(), supportingResult.getId(), ResultMarkKind.INCORRECT, false, Instant.now()));
        return id;
    }

    private void remove(UUID id) {
        jdbc.update(
                "UPDATE result_mark SET removed_at=now(),removed_by_id=?,removal_reason='The basis"
                        + " is confirmed.' WHERE id=? AND workspace_id=?",
                developerId,
                id,
                workspace.getId());
        store.marked(new ResultMarkChanged(
                id, workspace.getId(), supportingResult.getId(), ResultMarkKind.INCORRECT, true, Instant.now()));
    }

    @Test
    void shouldRestoreOnlyUnsentMarkWithholdingAndKeepAnotherActiveMark() {
        prepare(false);
        UUID first = mark(), second = mark();
        assertThat(current().getStopReason()).isEqualTo(FeedbackStopReason.RESULT_MARKED_INCORRECT);
        remove(first);
        assertThat(current().getState()).isEqualTo(FeedbackState.WITHHELD);
        assertThat(children().getFirst().getWriteStartedAt()).isNull();
        remove(second);
        assertThat(current().getState()).isEqualTo(FeedbackState.READY);
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.PLANNED);
        sender.send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(results.findById(supportingResult.getId()).orElseThrow().getEndedAt())
                .isNull();
    }

    @Test
    void shouldPreserveAnAdministratorRejectionWhenTheMarkIsRemoved() {
        when(gate.requiresApproval(any())).thenReturn(true);
        prepare(false);
        store.decide(
                workspace.getId(),
                feedbackId,
                developerId,
                FeedbackDecisionAction.REJECT,
                null,
                "Do not send this guidance.",
                digests.current(workspace.getId(), feedbackId));
        UUID id = mark();
        remove(id);
        assertThat(current().getState()).isEqualTo(FeedbackState.REJECTED);
        sender.send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isZero();
    }

    @Test
    void shouldRestoreTheSameConfirmedSummaryAfterAMarkWithoutAnotherPost() {
        prepare(false);
        sender.send(workspace.getId(), feedbackId);
        var copy = children().getFirst();
        UUID id = mark();
        assertThat(sends.findById(feedbackId).orElseThrow().getAction()).isEqualTo(FeedbackSendAction.RETRACT);
        newSender().send(workspace.getId(), feedbackId);
        assertThat(children().getFirst().getState()).isEqualTo(FeedbackCopyState.RETRACTED);
        remove(id);
        newSender().send(workspace.getId(), feedbackId);
        assertThat(provider.summaryCreates).isEqualTo(1);
        assertThat(provider.updates).isEqualTo(2);
        assertThat(children().getFirst().getExternalRef()).isEqualTo(copy.getExternalRef());
        assertThat(current().getDeliveredAt()).isNotNull();
    }
}

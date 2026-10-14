package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.result.ResultMarkService;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkChanged;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ResultMarkFeedbackIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Autowired
    FeedbackStoreService store;

    @Autowired
    ResultMarkService marks;

    @Test
    void shouldWithholdUnsentFeedbackWithoutEndingItsResultOnAnIncorrectMark() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        store.marked(new ResultMarkChanged(
                UUID.randomUUID(), workspace.getId(), result.getId(), ResultMarkKind.INCORRECT, false, AT));
        var row = feedback.findById(parent.getId()).orElseThrow();
        assertThat(row.getState()).isEqualTo(FeedbackState.WITHHELD);
        assertThat(row.getStopReason()).isEqualTo(FeedbackStopReason.RESULT_MARKED_INCORRECT);
        assertThat(results.findById(result.getId()).orElseThrow().getEndedAt()).isNull();
    }

    @Test
    void shouldKeepUnknownVisibilityWhenTheBasisIsMarkedIncorrect() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        var result = result();
        bind(parent, result);
        var copy = summary(parent, FeedbackCopyState.WRITE_STARTED);
        store.marked(new ResultMarkChanged(
                UUID.randomUUID(), workspace.getId(), result.getId(), ResultMarkKind.INCORRECT, false, AT));
        assertThat(feedback.findById(parent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copies.findById(copy.getId()).orElseThrow().getState()).isEqualTo(FeedbackCopyState.WRITE_STARTED);
        assertThat(sends.findById(parent.getId())).isEmpty();
    }

    @Test
    void shouldKeepAcceptedMarksOutOfDeliveryMutation() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        store.marked(new ResultMarkChanged(
                UUID.randomUUID(), workspace.getId(), result.getId(), ResultMarkKind.ACCEPTED, false, AT));
        assertThat(feedback.findById(parent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.READY);
    }

    @Test
    void shouldKeepFeedbackReadyWhenAnAcceptedMarkIsRemoved() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = negativeResult();
        bind(parent, result);
        var mark = marks.create(
                workspace.getId(), result.getId(), ResultMarkKind.ACCEPTED, developerId, "This tradeoff is accepted");
        assertThat(feedback.findById(parent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.READY);
        marks.remove(workspace.getId(), result.getId(), mark.getId(), developerId, "Reconsider the tradeoff");
        assertThat(feedback.findById(parent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.READY);
        assertThat(results.findById(result.getId()).orElseThrow().getEndedAt()).isNull();
    }

    @Test
    void shouldKeepFeedbackWithheldWhenItsModeIsOffBeforeIncorrectMarkRemoval() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = negativeResult();
        bind(parent, result);
        var mark = marks.create(
                workspace.getId(), result.getId(), ResultMarkKind.INCORRECT, developerId, "The basis is incorrect");
        jdbc.update(
                "UPDATE practice SET feedback_mode='OFF' WHERE workspace_id=? AND id=?",
                workspace.getId(),
                result.getPracticeId());
        marks.remove(workspace.getId(), result.getId(), mark.getId(), developerId, "The basis is verified");
        assertThat(feedback.findById(parent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.WITHHELD);
    }
}

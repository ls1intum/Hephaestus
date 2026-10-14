package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackGate;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeFeedbackDeliveryPolicyTest {

    @Test
    void shouldEvaluateFreshApprovalAtEgress() {
        var gate = mock(FeedbackGate.class);
        var feedback = Feedback.builder().build();
        var denied = new FeedbackCheck(
                FeedbackCheckStage.EGRESS,
                false,
                FeedbackStopReason.APPROVAL_STALE,
                Instant.EPOCH,
                new FeedbackCheckFacts(0, 0, true, true, true, true, false, true, false, false));
        when(gate.evaluate(feedback, FeedbackCheckStage.EGRESS, true)).thenReturn(denied);
        assertThat(new PracticeFeedbackDeliveryPolicy(gate).evaluate(feedback, FeedbackCheckStage.EGRESS))
                .isEqualTo(denied);
    }

    @Test
    void shouldAllowCompositionToRecordTheNeedForApprovalWithoutPretendingItWasApproved() {
        var gate = mock(FeedbackGate.class);
        var feedback = Feedback.builder().build();
        var allowed = new FeedbackCheck(
                FeedbackCheckStage.COMPOSITION,
                true,
                null,
                Instant.EPOCH,
                new FeedbackCheckFacts(0, 0, true, true, true, true, false, true, true, false));
        when(gate.evaluate(feedback, FeedbackCheckStage.COMPOSITION, false)).thenReturn(allowed);
        assertThat(new PracticeFeedbackDeliveryPolicy(gate).evaluate(feedback, FeedbackCheckStage.COMPOSITION))
                .isEqualTo(allowed);
    }
}

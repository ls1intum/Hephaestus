package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class InContextDeliveryGateTest {

    @Test
    void shouldDenyPrivateFeedbackEvenWhenDeliveryPolicyAllowsIt() {
        var policy = mock(PracticeFeedbackDeliveryPolicy.class);
        assertThat(new InContextDeliveryGate(policy)
                        .allows(Feedback.builder()
                                .destination(FeedbackDestination.CONVERSATION)
                                .build()))
                .isFalse();
    }

    @Test
    void shouldDenyPublicFeedbackWhenTheCurrentGateRefusesIt() {
        var policy = mock(PracticeFeedbackDeliveryPolicy.class);
        var feedback =
                Feedback.builder().destination(FeedbackDestination.ON_WORK).build();
        when(policy.evaluate(feedback, FeedbackCheckStage.EGRESS))
                .thenReturn(new FeedbackCheck(
                        FeedbackCheckStage.EGRESS,
                        false,
                        FeedbackStopReason.NO_AI,
                        Instant.EPOCH,
                        new FeedbackCheckFacts(0, 0, true, false, false, true, false, true, true, false)));
        assertThat(new InContextDeliveryGate(policy).allows(feedback)).isFalse();
    }
}

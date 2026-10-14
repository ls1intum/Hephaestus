package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnServerRole
public class InContextDeliveryGate {
    private final PracticeFeedbackDeliveryPolicy policy;

    public boolean allows(Feedback feedback) {
        return feedback.getDestination() == FeedbackDestination.ON_WORK
                && policy.evaluate(feedback, FeedbackCheckStage.EGRESS).allowed();
    }
}

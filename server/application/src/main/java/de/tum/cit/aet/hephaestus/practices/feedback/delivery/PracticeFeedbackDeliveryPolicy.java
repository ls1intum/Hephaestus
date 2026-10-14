package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackGate;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnServerRole
public class PracticeFeedbackDeliveryPolicy {
    private final FeedbackGate gate;

    public FeedbackCheck evaluate(Feedback feedback, FeedbackCheckStage stage) {
        return gate.evaluate(feedback, stage, stage != FeedbackCheckStage.COMPOSITION);
    }
}

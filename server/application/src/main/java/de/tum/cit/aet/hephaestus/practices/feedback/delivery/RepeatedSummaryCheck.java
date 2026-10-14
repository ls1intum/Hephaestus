package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class RepeatedSummaryCheck {
    private final FeedbackRepository feedback;

    public boolean repeats(long workspaceId, long itemId, long developerId, String body) {
        return feedback.findLastConfirmedSummary(workspaceId, itemId, developerId)
                .filter(prior -> body.equals(prior.getBody()))
                .isPresent();
    }
}

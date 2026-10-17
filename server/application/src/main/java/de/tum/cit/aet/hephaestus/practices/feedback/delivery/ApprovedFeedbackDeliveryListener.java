package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.ApprovedFeedbackReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ApprovedFeedbackDeliveryListener {
    private final FeedbackSendService sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void approved(ApprovedFeedbackReadyEvent event) {
        sender.send(event.workspaceId(), event.feedbackId());
    }
}

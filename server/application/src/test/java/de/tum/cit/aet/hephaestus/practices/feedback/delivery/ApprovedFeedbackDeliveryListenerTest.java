package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.ApprovedFeedbackReadyEvent;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ApprovedFeedbackDeliveryListenerTest {

    @Test
    void shouldUseTheSameDurableSenderForAnApprovedPiece() {
        var sender = mock(FeedbackSendService.class);
        var id = UUID.randomUUID();
        new ApprovedFeedbackDeliveryListener(sender).approved(new ApprovedFeedbackReadyEvent(4, id));
        verify(sender).send(4, id);
    }
}

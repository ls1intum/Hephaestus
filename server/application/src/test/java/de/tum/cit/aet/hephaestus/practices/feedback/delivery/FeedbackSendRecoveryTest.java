package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSend;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FeedbackSendRecoveryTest {

    @Test
    void shouldRecoverTheTenantAndFeedbackIdFromTheDurableSend() {
        var sends = mock(FeedbackSendRepository.class);
        var sender = mock(FeedbackSendService.class);
        var id = UUID.randomUUID();
        when(sends.findDue(Instant.EPOCH, 100))
                .thenReturn(List.of(FeedbackSend.builder()
                        .workspaceId(8)
                        .feedbackId(id)
                        .state(FeedbackSendState.UNCERTAIN)
                        .build()));
        new FeedbackSendRecovery(sends, sender, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).recover();
        verify(sender).send(8, id);
    }
}

package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConversationalFeedbackDeliveryLoopIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Autowired
    ConversationFeedbackTtlPurger purger;

    @Test
    void shouldExpireOnlyUnfencedReadyConversationFeedbackAndRetainItsHistory() {
        var unsent = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        var unknown = piece(FeedbackDestination.CONVERSATION, FeedbackState.UNCONFIRMED, null);
        UUID copyId = FeedbackIds.copy(unknown.getId(), FeedbackCopyKind.CHAT_TURN, 0);
        copies.saveAndFlush(FeedbackCopy.builder()
                .id(copyId)
                .workspaceId(workspace.getId())
                .feedbackId(unknown.getId())
                .kind(FeedbackCopyKind.CHAT_TURN)
                .state(FeedbackCopyState.WRITE_STARTED)
                .writeStartedAt(AT)
                .writeKey(copyId.toString())
                .body("Try a focused test.")
                .createdAt(AT)
                .build());
        purger.expireWorkspaceBefore(workspace.getId(), AT.plusSeconds(1));
        assertThat(feedback.findById(unsent.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.WITHHELD);
        assertThat(feedback.findById(unsent.getId()).orElseThrow().getStopReason())
                .isEqualTo(FeedbackStopReason.CONVERSATION_EXPIRED);
        assertThat(feedback.findById(unknown.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copies.findById(copyId).orElseThrow().getWriteStartedAt()).isEqualTo(AT);
    }
}

package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackSupersessionIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Autowired
    FeedbackSupersession supersession;

    @Test
    void shouldReplaceAPrivateCardWithoutErasingItsFirstRead() {
        var old = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.DELIVERED, AT);
        var next = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        assertThat(supersession.replaceOpen(workspace.getId(), old.getId(), next.getId()))
                .isTrue();
        assertThat(feedback.findById(old.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.REPLACED);
        assertThat(feedback.findById(old.getId()).orElseThrow().getDeliveredAt())
                .isEqualTo(AT);
        assertThat(feedback.findById(next.getId()).orElseThrow().getReplacesId())
                .isEqualTo(old.getId());
    }

    @Test
    void shouldNotReplaceAPossiblyShownConversationTurn() {
        var old = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        var next = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        UUID copyId = FeedbackIds.copy(old.getId(), FeedbackCopyKind.CHAT_TURN, 0);
        copies.saveAndFlush(FeedbackCopy.builder()
                .id(copyId)
                .workspaceId(workspace.getId())
                .feedbackId(old.getId())
                .kind(FeedbackCopyKind.CHAT_TURN)
                .state(FeedbackCopyState.WRITE_STARTED)
                .writeStartedAt(AT)
                .writeKey(copyId.toString())
                .body("Already started")
                .createdAt(AT)
                .build());
        assertThat(supersession.replaceOpen(workspace.getId(), old.getId(), next.getId()))
                .isFalse();
        assertThat(feedback.findById(old.getId()).orElseThrow().getState()).isEqualTo(FeedbackState.READY);
    }
}

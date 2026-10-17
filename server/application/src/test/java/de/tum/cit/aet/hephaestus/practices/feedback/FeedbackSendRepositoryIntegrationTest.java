package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import org.junit.jupiter.api.Test;

class FeedbackSendRepositoryIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Test
    void shouldReclaimAnExpiredLeaseWithoutClearingThePhysicalWriteFence() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        var copy = summary(parent, FeedbackCopyState.WRITE_STARTED);
        sends.saveAndFlush(FeedbackSend.builder()
                .feedbackId(parent.getId())
                .workspaceId(workspace.getId())
                .action(FeedbackSendAction.POST)
                .state(FeedbackSendState.PENDING)
                .attempts(0)
                .nextAttemptAt(AT)
                .createdAt(AT)
                .updatedAt(AT)
                .build());
        assertThat(sends.claim(workspace.getId(), parent.getId(), "first", AT, AT.plusSeconds(1)))
                .isEqualTo(1);
        assertThat(sends.claim(workspace.getId(), parent.getId(), "other", AT, AT.plusSeconds(1)))
                .isZero();
        assertThat(sends.claim(workspace.getId(), parent.getId(), "recovery", AT.plusSeconds(2), AT.plusSeconds(122)))
                .isEqualTo(1);
        assertThat(sends.settle(
                        workspace.getId(), parent.getId(), "first", "DONE", AT.plusSeconds(2), AT.plusSeconds(3), null))
                .isZero();
        var retained = copies.findById(copy.getId()).orElseThrow();
        assertThat(retained.getWriteStartedAt()).isEqualTo(AT);
        assertThat(retained.getWriteMarker()).isEqualTo(copy.getWriteMarker());
    }

    @Test
    void shouldReopenOnlyCopiesWithProvedAbsenceAndKeepTheirFirstFence() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        var copy = summary(parent, FeedbackCopyState.WRITE_STARTED);
        assertThat(copies.retryAbsent(workspace.getId(), parent.getId())).isZero();
        copies.recordAbsent(workspace.getId(), parent.getId(), copy.getId(), "Exact readback");
        assertThat(copies.retryAbsent(workspace.getId(), parent.getId())).isEqualTo(1);
        assertThat(copies.beginWrite(workspace.getId(), parent.getId(), copy.getId(), AT.plusSeconds(30)))
                .isEqualTo(1);
        assertThat(copies.findById(copy.getId()).orElseThrow().getWriteStartedAt())
                .isEqualTo(AT);
    }

    @Test
    void shouldIgnoreUnknownCopiesInTheLastConfirmedSummary() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        summary(parent, FeedbackCopyState.WRITE_STARTED);
        assertThat(feedback.findLastConfirmedSummary(workspace.getId(), itemId, developerId))
                .isEmpty();
    }

    @Test
    void shouldUseAConfirmedSummaryEvenWhenASiblingLeavesTheParentUnconfirmed() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, AT);
        summary(parent, FeedbackCopyState.POSTED);
        var retained = feedback.findLastConfirmedSummary(workspace.getId(), itemId, developerId)
                .orElseThrow();
        assertThat(retained.getId()).isEqualTo(parent.getId());
        assertThat(retained.getBody()).isEqualTo(parent.getBody());
    }
}

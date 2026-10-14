package de.tum.cit.aet.hephaestus.practices.feedback.page;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import org.junit.jupiter.api.Test;

class PracticePageFeedbackLifecycleIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Test
    void shouldRecordFirstReadOnTheParentWithoutCreatingAPhysicalCardCopy() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        assertThat(feedback.recordFirstRead(
                        workspace.getId(),
                        parent.getId(),
                        developerId,
                        AT,
                        FeedbackDestination.PRACTICE_PAGE,
                        FeedbackState.READY,
                        FeedbackState.DELIVERED))
                .isEqualTo(1);
        assertThat(feedback.recordFirstRead(
                        workspace.getId(),
                        parent.getId(),
                        developerId,
                        AT.plusSeconds(10),
                        FeedbackDestination.PRACTICE_PAGE,
                        FeedbackState.READY,
                        FeedbackState.DELIVERED))
                .isZero();
        var row = feedback.findById(parent.getId()).orElseThrow();
        assertThat(row.getDeliveredAt()).isEqualTo(AT);
        assertThat(row.getState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(workspace.getId(), parent.getId()))
                .isEmpty();
    }

    @Test
    void shouldDenyAnotherDevelopersFirstRead() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        assertThat(feedback.recordFirstRead(
                        workspace.getId(),
                        parent.getId(),
                        developerId + 1,
                        AT,
                        FeedbackDestination.PRACTICE_PAGE,
                        FeedbackState.READY,
                        FeedbackState.DELIVERED))
                .isZero();
        assertThat(feedback.findById(parent.getId()).orElseThrow().getDeliveredAt())
                .isNull();
    }
}

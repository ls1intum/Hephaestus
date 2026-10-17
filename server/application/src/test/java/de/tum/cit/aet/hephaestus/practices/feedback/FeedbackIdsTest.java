package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FeedbackIdsTest {

    private static final UUID REVIEW = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID RESULT = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

    @Test
    void shouldKeepThePhysicalWriteIdentityWhenPreparationIsRepeated() {
        UUID feedback = FeedbackIds.onWork(7, REVIEW, 9);
        UUID copy = FeedbackIds.copy(feedback, FeedbackCopyKind.SUMMARY, 0);
        assertThat(feedback).isEqualTo(UUID.fromString("9af9ade2-770e-58f3-b1ef-0585cc91b6b2"));
        assertThat(copy).isEqualTo(UUID.fromString("274b3fc2-4e2c-5f84-8ce3-e310ba921388"));
        assertThat(copy.version()).isEqualTo(5);
        assertThat(copy.variant()).isEqualTo(2);
        assertThat(FeedbackIds.basis(feedback, RESULT, null))
                .isEqualTo(UUID.fromString("80753e1f-1c33-56f1-998f-79dd3ce4ca8b"));
        assertThat(FeedbackIds.writeMarker(copy))
                .isEqualTo("<!-- hephaestus-feedback-copy:274b3fc2-4e2c-5f84-8ce3-e310ba921388 -->");
        assertThat(FeedbackIds.copy(FeedbackIds.onWork(7, REVIEW, 9), FeedbackCopyKind.SUMMARY, 0))
                .isEqualTo(copy);
    }

    @Test
    void shouldPrepareOneConversationPieceWhenTheSameResultsArriveInAnotherOrder() {
        UUID other = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        assertThat(FeedbackIds.conversation(7, REVIEW, 9, List.of(RESULT, other, RESULT)))
                .isEqualTo(FeedbackIds.conversation(7, REVIEW, 9, List.of(other, RESULT)));
    }

    @Test
    void shouldKeepWorkspacesAndDevelopersSeparateWhenReviewsShareTheirUuid() {
        UUID feedback = FeedbackIds.onWork(7, REVIEW, 9);
        assertThat(FeedbackIds.onWork(8, REVIEW, 9)).isNotEqualTo(feedback);
        assertThat(FeedbackIds.onWork(7, REVIEW, 10)).isNotEqualTo(feedback);
        assertThat(FeedbackIds.practicePage(7, REVIEW, 9, RESULT)).isNotEqualTo(feedback);
    }

    @Test
    void shouldRejectAnInvalidIdentityWhenPreparingFeedback() {
        assertThatThrownBy(() -> FeedbackIds.onWork(0, REVIEW, 9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedbackIds.copy(REVIEW, FeedbackCopyKind.SUMMARY, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedbackIds.conversation(7, REVIEW, 9, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

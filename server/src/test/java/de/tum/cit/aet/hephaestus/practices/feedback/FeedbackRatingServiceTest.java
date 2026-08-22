package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class FeedbackRatingServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long RECIPIENT_ID = 10L;
    private static final UUID FEEDBACK_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackRatingRepository ratingRepository;

    @Mock
    private CurrentDeveloperLookup currentDeveloperLookup;

    private FeedbackRatingService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new FeedbackRatingService(feedbackRepository, ratingRepository, currentDeveloperLookup);
        workspaceContext = new WorkspaceContext(
            WORKSPACE_ID,
            "test-workspace",
            "Test workspace",
            null,
            null,
            false,
            false,
            Set.of()
        );
    }

    @Test
    void shouldStoreRatingAndCommentWhenFeedbackIsDeliveredToCurrentUser() {
        Feedback feedback = deliveredFeedback(FEEDBACK_ID, RECIPIENT_ID);
        FeedbackRating storedRating = mock(FeedbackRating.class);
        Instant updatedAt = Instant.parse("2026-08-17T08:00:00Z");
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(Optional.of(feedback));
        when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(RECIPIENT_ID);
        when(ratingRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(storedRating));
        when(storedRating.getFeedbackId()).thenReturn(FEEDBACK_ID);
        when(storedRating.getState()).thenReturn(FeedbackRatingState.INCORRECT);
        when(storedRating.getComment()).thenReturn("This refers to a different method.");
        when(storedRating.getUpdatedAt()).thenReturn(updatedAt);

        var result = service.upsert(
            workspaceContext,
            FEEDBACK_ID,
            FeedbackRatingState.INCORRECT,
            "This refers to a different method."
        );

        verify(ratingRepository).upsert(FEEDBACK_ID, "INCORRECT", "This refers to a different method.");
        assertThat(result.feedbackId()).isEqualTo(FEEDBACK_ID);
        assertThat(result.state()).isEqualTo(FeedbackRatingState.INCORRECT);
        assertThat(result.comment()).isEqualTo("This refers to a different method.");
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldKeepRatingsIndependentForFeedbackDeliveredToDifferentRecipients() {
        UUID otherFeedbackId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        Long otherRecipientId = 11L;
        FeedbackRating firstRating = storedRating(FEEDBACK_ID, FeedbackRatingState.HELPFUL);
        FeedbackRating secondRating = storedRating(otherFeedbackId, FeedbackRatingState.UNHELPFUL);
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(FEEDBACK_ID, RECIPIENT_ID))
        );
        when(feedbackRepository.findByIdAndWorkspaceId(otherFeedbackId, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(otherFeedbackId, otherRecipientId))
        );
        when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(RECIPIENT_ID, otherRecipientId);
        when(ratingRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(firstRating));
        when(ratingRepository.findById(otherFeedbackId)).thenReturn(Optional.of(secondRating));

        var first = service.upsert(workspaceContext, FEEDBACK_ID, FeedbackRatingState.HELPFUL, null);
        var second = service.upsert(workspaceContext, otherFeedbackId, FeedbackRatingState.UNHELPFUL, null);

        verify(ratingRepository).upsert(FEEDBACK_ID, "HELPFUL", null);
        verify(ratingRepository).upsert(otherFeedbackId, "UNHELPFUL", null);
        assertThat(first.state()).isEqualTo(FeedbackRatingState.HELPFUL);
        assertThat(second.state()).isEqualTo(FeedbackRatingState.UNHELPFUL);
    }

    @Test
    void shouldRejectRatingFromAnyoneExceptRecipient() {
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(FEEDBACK_ID, RECIPIENT_ID))
        );
        when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(99L);

        assertThatThrownBy(() -> service.upsert(workspaceContext, FEEDBACK_ID, FeedbackRatingState.UNHELPFUL, null))
            .isInstanceOf(AccessForbiddenException.class)
            .hasMessageContaining("recipient");
        verify(ratingRepository, never()).upsert(FEEDBACK_ID, "UNHELPFUL", null);
    }

    @Test
    void shouldRejectRatingForUndeliveredFeedback() {
        Feedback feedback = Feedback.builder()
            .id(FEEDBACK_ID)
            .workspaceId(WORKSPACE_ID)
            .recipientUserId(RECIPIENT_ID)
            .deliveryState(FeedbackDeliveryState.SUPPRESSED)
            .build();
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(Optional.of(feedback));
        when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(RECIPIENT_ID);

        assertThatThrownBy(() -> service.upsert(workspaceContext, FEEDBACK_ID, FeedbackRatingState.HELPFUL, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delivered");
        verify(ratingRepository, never()).upsert(FEEDBACK_ID, "HELPFUL", null);
    }

    @Test
    void shouldRemoveExistingRatingForDeliveredFeedback() {
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(FEEDBACK_ID, RECIPIENT_ID))
        );
        when(currentDeveloperLookup.currentDeveloperIdElseThrow()).thenReturn(RECIPIENT_ID);

        service.delete(workspaceContext, FEEDBACK_ID);

        verify(ratingRepository).deleteById(FEEDBACK_ID);
    }

    private Feedback deliveredFeedback(UUID feedbackId, Long recipientId) {
        return Feedback.builder()
            .id(feedbackId)
            .workspaceId(WORKSPACE_ID)
            .recipientUserId(recipientId)
            .deliveryState(FeedbackDeliveryState.DELIVERED)
            .build();
    }

    private FeedbackRating storedRating(UUID feedbackId, FeedbackRatingState state) {
        FeedbackRating rating = mock(FeedbackRating.class);
        when(rating.getFeedbackId()).thenReturn(feedbackId);
        when(rating.getState()).thenReturn(state);
        when(rating.getUpdatedAt()).thenReturn(Instant.parse("2026-08-17T08:00:00Z"));
        return rating;
    }
}

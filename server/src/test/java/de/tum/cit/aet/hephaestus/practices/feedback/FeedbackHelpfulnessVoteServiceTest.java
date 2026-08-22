package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class FeedbackHelpfulnessVoteServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long RECIPIENT_ID = 10L;
    private static final UUID FEEDBACK_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackHelpfulnessVoteRepository voteRepository;

    @Mock
    private UserRepository userRepository;

    private FeedbackHelpfulnessVoteService service;
    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        service = new FeedbackHelpfulnessVoteService(feedbackRepository, voteRepository, userRepository);
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
    void shouldStoreHelpfulnessWhenFeedbackIsDeliveredToCurrentUser() {
        Feedback feedback = deliveredFeedback(RECIPIENT_ID);
        User currentUser = user(RECIPIENT_ID);
        FeedbackHelpfulnessVote storedVote = mock(FeedbackHelpfulnessVote.class);
        Instant updatedAt = Instant.parse("2026-08-17T08:00:00Z");
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(Optional.of(feedback));
        when(userRepository.getCurrentUserElseThrow()).thenReturn(currentUser);
        when(voteRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(storedVote));
        when(storedVote.getFeedbackId()).thenReturn(FEEDBACK_ID);
        when(storedVote.getHelpful()).thenReturn(true);
        when(storedVote.getUpdatedAt()).thenReturn(updatedAt);

        var result = service.upsert(workspaceContext, FEEDBACK_ID, true);

        verify(voteRepository).upsert(FEEDBACK_ID, true);
        assertThat(result.feedbackId()).isEqualTo(FEEDBACK_ID);
        assertThat(result.helpful()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldRejectRatingFromAnyoneExceptRecipient() {
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(RECIPIENT_ID))
        );
        when(userRepository.getCurrentUserElseThrow()).thenReturn(user(99L));

        assertThatThrownBy(() -> service.upsert(workspaceContext, FEEDBACK_ID, false))
            .isInstanceOf(AccessForbiddenException.class)
            .hasMessageContaining("recipient");
        verify(voteRepository, never()).upsert(FEEDBACK_ID, false);
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
        when(userRepository.getCurrentUserElseThrow()).thenReturn(user(RECIPIENT_ID));

        assertThatThrownBy(() -> service.upsert(workspaceContext, FEEDBACK_ID, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delivered");
        verify(voteRepository, never()).upsert(FEEDBACK_ID, true);
    }

    @Test
    void shouldRemoveExistingRatingForDeliveredFeedback() {
        when(feedbackRepository.findByIdAndWorkspaceId(FEEDBACK_ID, WORKSPACE_ID)).thenReturn(
            Optional.of(deliveredFeedback(RECIPIENT_ID))
        );
        when(userRepository.getCurrentUserElseThrow()).thenReturn(user(RECIPIENT_ID));

        service.delete(workspaceContext, FEEDBACK_ID);

        verify(voteRepository).deleteById(FEEDBACK_ID);
    }

    private Feedback deliveredFeedback(Long recipientId) {
        return Feedback.builder()
            .id(FEEDBACK_ID)
            .workspaceId(WORKSPACE_ID)
            .recipientUserId(recipientId)
            .deliveryState(FeedbackDeliveryState.DELIVERED)
            .build();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}

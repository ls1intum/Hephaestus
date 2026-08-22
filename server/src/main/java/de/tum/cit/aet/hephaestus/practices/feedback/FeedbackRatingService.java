package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackRatingDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores the recipient's current assessment of delivered feedback. */
@Service
@RequiredArgsConstructor
public class FeedbackRatingService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackRatingRepository ratingRepository;
    private final CurrentDeveloperLookup currentDeveloperLookup;

    @Transactional
    public FeedbackRatingDTO upsert(
        WorkspaceContext workspaceContext,
        UUID feedbackId,
        FeedbackRatingState state,
        @Nullable String comment
    ) {
        requireDeliveredFeedbackForCurrentRecipient(workspaceContext, feedbackId);
        ratingRepository.upsert(feedbackId, state.name(), comment);
        return ratingRepository
            .findById(feedbackId)
            .map(FeedbackRatingDTO::from)
            .orElseThrow(() -> new IllegalStateException("Feedback rating upsert returned no row for " + feedbackId));
    }

    @Transactional
    public void delete(WorkspaceContext workspaceContext, UUID feedbackId) {
        requireDeliveredFeedbackForCurrentRecipient(workspaceContext, feedbackId);
        ratingRepository.deleteById(feedbackId);
    }

    private Feedback requireDeliveredFeedbackForCurrentRecipient(WorkspaceContext workspaceContext, UUID feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        Long currentUserId = currentDeveloperLookup.currentDeveloperIdElseThrow();
        if (!feedback.getRecipientUserId().equals(currentUserId)) {
            throw new AccessForbiddenException("Only the feedback recipient can rate it");
        }
        if (feedback.getDeliveryState() != FeedbackDeliveryState.DELIVERED) {
            throw new IllegalArgumentException("Only delivered feedback can be rated");
        }
        return feedback;
    }
}

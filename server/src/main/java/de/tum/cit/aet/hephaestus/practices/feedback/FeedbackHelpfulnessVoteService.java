package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackHelpfulnessVoteDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores usefulness separately from action/validity reactions and from learner standing. */
@Service
@RequiredArgsConstructor
public class FeedbackHelpfulnessVoteService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackHelpfulnessVoteRepository voteRepository;
    private final CurrentDeveloperLookup currentDeveloperLookup;

    @Transactional
    public FeedbackHelpfulnessVoteDTO upsert(WorkspaceContext workspaceContext, UUID feedbackId, boolean helpful) {
        requireDeliveredFeedbackForCurrentRecipient(workspaceContext, feedbackId);
        voteRepository.upsert(feedbackId, helpful);
        return voteRepository
            .findById(feedbackId)
            .map(FeedbackHelpfulnessVoteDTO::from)
            .orElseThrow(() -> new IllegalStateException("Helpfulness vote upsert returned no row for " + feedbackId));
    }

    @Transactional
    public void delete(WorkspaceContext workspaceContext, UUID feedbackId) {
        requireDeliveredFeedbackForCurrentRecipient(workspaceContext, feedbackId);
        voteRepository.deleteById(feedbackId);
    }

    private Feedback requireDeliveredFeedbackForCurrentRecipient(WorkspaceContext workspaceContext, UUID feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        Long currentUserId = currentDeveloperLookup.currentDeveloperIdElseThrow();
        if (!feedback.getRecipientUserId().equals(currentUserId)) {
            throw new AccessForbiddenException("Only the feedback recipient can rate its usefulness");
        }
        if (feedback.getDeliveryState() != FeedbackDeliveryState.DELIVERED) {
            throw new IllegalArgumentException("Only delivered feedback can be rated");
        }
        return feedback;
    }
}

package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseRequestDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the recipient's response to delivered feedback as an append-only timeline.
 *
 * <p>Two questions, answerable apart: how useful the feedback was, and what the recipient decided to do with
 * it. Each submit appends what was actually said and nothing else, so the record can show that someone rated a
 * unit helpful before disputing it. Reading the current answer is therefore a fold across rows, which
 * {@link ReactionRepository} owns.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FeedbackResponseService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackResponseService.class);

    private final ReactionRepository reactionRepository;
    private final FeedbackRepository feedbackRepository;
    private final CurrentDeveloperLookup currentDeveloperLookup;

    /**
     * Appends what the recipient just said and returns their answer as it now stands.
     *
     * <p>The returned state is re-read rather than built from the request: a submit that answers only one
     * question leaves the other one standing, and the caller needs the whole answer to render it.
     */
    public FeedbackResponseDTO submitResponse(
        WorkspaceContext workspaceContext,
        UUID feedbackId,
        FeedbackResponseRequestDTO request
    ) {
        Feedback feedback = requireDeliveredFeedback(workspaceContext.id(), feedbackId);
        long recipientId = currentDeveloperLookup.currentDeveloperIdElseThrow();
        requireRecipient(feedback, recipientId);
        validate(request);

        boolean withdrawing = Boolean.TRUE.equals(request.withdraw());
        Reaction response = Reaction.builder()
            .feedback(feedback)
            .feedbackId(feedbackId)
            .reactorUserId(recipientId)
            .usefulness(withdrawing ? null : request.usefulness())
            .resolution(withdrawing ? null : request.resolution())
            .explanation(withdrawing ? null : request.comment())
            .recurrenceKey(feedbackRepository.findHeadlineRecurrenceKey(feedbackId).orElse(null))
            .build();
        reactionRepository.save(response);

        log.info(
            "Recorded feedback response: feedbackId={}, usefulness={}, resolution={}, withdrawn={}, recipientUserId={}",
            feedbackId,
            response.getUsefulness(),
            response.getResolution(),
            withdrawing,
            recipientId
        );
        return currentResponse(feedbackId, recipientId).orElseGet(() -> FeedbackResponseDTO.none(feedbackId));
    }

    /** The recipient's answer as it currently stands, or empty when they have said nothing that still holds. */
    @Transactional(readOnly = true)
    public Optional<FeedbackResponseDTO> getLatestResponse(WorkspaceContext workspaceContext, UUID feedbackId) {
        Feedback feedback = requireDeliveredFeedback(workspaceContext.id(), feedbackId);
        long recipientId = currentDeveloperLookup.currentDeveloperIdElseThrow();
        requireRecipient(feedback, recipientId);
        return currentResponse(feedbackId, recipientId);
    }

    @Transactional(readOnly = true)
    public FeedbackEngagementDTO getEngagement(WorkspaceContext workspaceContext) {
        long recipientId = currentDeveloperLookup.currentDeveloperIdElseThrow();
        Map<FeedbackResolution, Long> counts = new EnumMap<>(FeedbackResolution.class);
        reactionRepository
            .countByReactorAndWorkspaceGroupByAction(recipientId, workspaceContext.id())
            .forEach(projection ->
                counts.put(FeedbackResolution.valueOf(projection.getAction()), projection.getCount())
            );
        return new FeedbackEngagementDTO(
            counts.getOrDefault(FeedbackResolution.ADDRESSED, 0L),
            counts.getOrDefault(FeedbackResolution.DISPUTED, 0L),
            counts.getOrDefault(FeedbackResolution.NOT_APPLICABLE, 0L)
        );
    }

    private Optional<FeedbackResponseDTO> currentResponse(UUID feedbackId, long recipientId) {
        return reactionRepository
            .findCurrentResponse(feedbackId, recipientId)
            .filter(current -> current.getUsefulness() != null || current.getResolution() != null)
            .map(current -> FeedbackResponseDTO.from(feedbackId, current));
    }

    private Feedback requireDeliveredFeedback(long workspaceId, UUID feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        if (feedback.getDeliveryState() != FeedbackDeliveryState.DELIVERED) {
            throw new IllegalArgumentException("Only delivered feedback can receive a response");
        }
        return feedback;
    }

    private void requireRecipient(Feedback feedback, long currentUserId) {
        if (feedback.getRecipientUserId() != currentUserId) {
            throw new AccessForbiddenException("Only the feedback recipient can respond to it");
        }
    }

    private void validate(FeedbackResponseRequestDTO request) {
        if (Boolean.TRUE.equals(request.withdraw())) {
            if (request.usefulness() != null || request.resolution() != null || request.comment() != null) {
                throw new IllegalArgumentException("A withdrawal cannot carry an answer");
            }
            return;
        }
        // Withdrawing is the only way to say nothing. An empty request is a client that lost its state, and
        // silently erasing the recipient's answer for it would be the worst possible reading.
        if (request.usefulness() == null && request.resolution() == null) {
            throw new IllegalArgumentException("A feedback response requires usefulness or resolution");
        }
        if (
            request.resolution() == FeedbackResolution.DISPUTED &&
            (request.comment() == null || request.comment().isBlank())
        ) {
            throw new IllegalArgumentException("A comment is required when disputing feedback");
        }
    }
}

package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository.CurrentResponseProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The recipient's response to one delivered feedback unit, as it currently stands.
 *
 * <p>Not one stored row: the two dimensions are answered independently and may have been said at different
 * times, so this is the fold across everything the recipient has said and not withdrawn. {@code respondedAt}
 * is when they last said any of it.
 */
@Schema(description = "The recipient's current assessment and resolution of delivered feedback")
public record FeedbackResponseDTO(
    @NonNull UUID feedbackId,
    @Nullable FeedbackUsefulness usefulness,
    @Nullable FeedbackResolution resolution,
    @Nullable String comment,
    @Nullable Instant respondedAt
) {
    public static FeedbackResponseDTO from(UUID feedbackId, CurrentResponseProjection current) {
        return new FeedbackResponseDTO(
            feedbackId,
            current.getUsefulness() == null ? null : FeedbackUsefulness.valueOf(current.getUsefulness()),
            current.getResolution() == null ? null : FeedbackResolution.valueOf(current.getResolution()),
            current.getComment(),
            current.getRespondedAt()
        );
    }

    /** What a recipient who has just withdrawn everything now says: nothing. */
    public static FeedbackResponseDTO none(UUID feedbackId) {
        return new FeedbackResponseDTO(feedbackId, null, null, null, null);
    }
}

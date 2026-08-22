package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRating;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRatingState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The recipient's current rating for a delivered feedback unit")
public record FeedbackRatingDTO(
    @NonNull UUID feedbackId,
    @NonNull FeedbackRatingState state,
    @Nullable String comment,
    @NonNull Instant updatedAt
) {
    public static FeedbackRatingDTO from(FeedbackRating rating) {
        return new FeedbackRatingDTO(
            rating.getFeedbackId(),
            rating.getState(),
            rating.getComment(),
            rating.getUpdatedAt()
        );
    }
}

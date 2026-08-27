package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository.CurrentResponseProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    public static FeedbackResponseDTO none(UUID feedbackId) {
        return new FeedbackResponseDTO(feedbackId, null, null, null, null);
    }
}

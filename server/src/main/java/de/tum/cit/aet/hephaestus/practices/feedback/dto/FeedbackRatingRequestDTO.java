package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRatingState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Mark delivered practice feedback as helpful, unhelpful, or incorrect")
public record FeedbackRatingRequestDTO(
    @NotNull @Schema(description = "The recipient's assessment") FeedbackRatingState state,
    @Nullable
    @Size(max = 2000)
    @Schema(description = "Optional comment explaining the assessment", maxLength = 2000)
    String comment
) {}

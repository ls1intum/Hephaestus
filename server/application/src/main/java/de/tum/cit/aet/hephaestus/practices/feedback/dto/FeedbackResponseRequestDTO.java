package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * What the recipient is saying about one delivered piece of feedback right now.
 *
 * <p>This is a complete replacement. Omitted dimensions are cleared.
 */
@Schema(description = "The complete response to delivered feedback")
public record FeedbackResponseRequestDTO(
        @Nullable @Schema(description = "How useful the feedback was")
        FeedbackUsefulness usefulness,

        @Nullable @Schema(description = "What the recipient decided to do with the feedback")
        FeedbackResolution resolution,

        @Nullable
        @Size(max = 2000)
        @Schema(description = "Optional explanation; required when resolution is DISPUTED.", maxLength = 2000)
        String comment) {}

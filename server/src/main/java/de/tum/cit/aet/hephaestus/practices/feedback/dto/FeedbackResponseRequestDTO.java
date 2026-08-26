package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * What the recipient is saying about one delivered feedback unit right now.
 *
 * <p>Answer either question or both; whatever is left out keeps whatever was said before. Withdrawing is the
 * one way to say nothing, and it is a flag rather than an empty body so that a client which lost its state
 * cannot erase an answer by accident.
 */
@Schema(description = "Assess delivered feedback, say what you will do with it, or take your answer back")
public record FeedbackResponseRequestDTO(
    @Nullable @Schema(description = "How useful the feedback was") FeedbackUsefulness usefulness,
    @Nullable @Schema(description = "What the recipient decided to do with the feedback") FeedbackResolution resolution,
    @Nullable
    @Size(max = 2000)
    @Schema(description = "Optional explanation; required when resolution is DISPUTED", maxLength = 2000)
    String comment,
    @Nullable
    @Schema(description = "Take the whole response back, leaving no answer on record. Carries nothing else.")
    Boolean withdraw
) {}

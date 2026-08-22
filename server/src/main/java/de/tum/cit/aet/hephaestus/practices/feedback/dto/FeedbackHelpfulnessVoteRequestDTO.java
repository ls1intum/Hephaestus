package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Rate whether delivered practice feedback was helpful")
public record FeedbackHelpfulnessVoteRequestDTO(
    @NotNull @Schema(description = "true when the feedback was helpful, false when it was not") Boolean helpful
) {}

package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to set how loud a practice is allowed to be in this workspace")
public record UpdatePracticeReviewTierRequestDTO(
    @NotNull(message = "Review tier is required")
    @Schema(
        description = "OFF = not reviewed · MEASURE = reviewed and recorded, silent · " +
            "COACH = also raised in the mentor conversation · ENGAGE = also placed on the artifact"
    )
    PracticeReviewTier reviewTier
) {}

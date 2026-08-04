package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to choose whether new reviews include a practice")
public record UpdatePracticeUsageRequestDTO(
    @NotNull(message = "Used-in-new-reviews state is required")
    @Schema(description = "Whether new reviews should include the practice")
    Boolean usedInNewReviews
) {}

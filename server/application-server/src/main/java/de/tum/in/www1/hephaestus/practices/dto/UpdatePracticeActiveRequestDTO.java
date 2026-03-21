package de.tum.in.www1.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for setting the active state of a practice.
 */
@Schema(description = "Request to set a practice's active state")
public record UpdatePracticeActiveRequestDTO(
    @NotNull(message = "Active state is required")
    @Schema(description = "Whether the practice should be active", requiredMode = Schema.RequiredMode.REQUIRED)
    Boolean active
) {}

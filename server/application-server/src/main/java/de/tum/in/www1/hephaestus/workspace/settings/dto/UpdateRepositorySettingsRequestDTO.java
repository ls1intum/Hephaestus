package de.tum.in.www1.hephaestus.workspace.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating repository contribution visibility settings in a workspace.
 *
 * @param hiddenFromContributions whether contributions from this repository should be hidden
 */
@Schema(description = "Request to update repository contribution visibility settings in a workspace")
public record UpdateRepositorySettingsRequestDTO(
    @NotNull(message = "hiddenFromContributions is required")
    @Schema(
        description = "Whether contributions from this repository should be hidden from leaderboard calculations",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Boolean hiddenFromContributions
) {}

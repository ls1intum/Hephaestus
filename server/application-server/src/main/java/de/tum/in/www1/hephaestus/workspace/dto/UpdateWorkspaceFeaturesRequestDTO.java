package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for updating workspace feature flags.
 * All fields are nullable — {@code null} means "no change" (PATCH semantics).
 */
@Schema(description = "Request to update workspace feature flags. Null fields are left unchanged.")
public record UpdateWorkspaceFeaturesRequestDTO(
    @Schema(description = "Enable best practices detection and tracking") Boolean practicesEnabled,
    @Schema(description = "Enable the achievements system") Boolean achievementsEnabled,
    @Schema(description = "Enable the leaderboard ranking page") Boolean leaderboardEnabled,
    @Schema(description = "Enable the league/progression system") Boolean progressionEnabled
) {}

package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating workspace leaderboard notification preferences.
 */
@Schema(description = "Request to update leaderboard notification settings")
public record UpdateWorkspaceNotificationsRequestDTO(
    @Schema(description = "Whether leaderboard notifications are enabled") Boolean enabled,
    @Schema(description = "Team name for filtering leaderboard notifications") String team,
    @Pattern(
        regexp = "^[CGD][A-Z0-9]{8,}$",
        message = "Channel ID must start with 'C' (public), 'G' (private), or 'D' (DM) followed by at least 8 alphanumeric characters"
    )
    @Schema(description = "Slack channel ID for notifications", example = "C01234567AB")
    String channelId
) {}

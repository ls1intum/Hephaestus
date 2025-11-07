package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating workspace leaderboard notification preferences.
 */
public record UpdateWorkspaceNotificationsRequestDTO(
    Boolean enabled,
    String team,
    @Pattern(regexp = "^C[A-Z0-9]{8,}$", message = "Channel ID must start with 'C' followed by at least 8 alphanumeric characters")
    String channelId
) {
}

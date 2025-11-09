package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating workspace leaderboard notification preferences.
 */
public record UpdateWorkspaceNotificationsRequestDTO(
    Boolean enabled,
    String team,
    @Pattern(
        regexp = "^[CGD][A-Z0-9]{8,}$",
        message = "Channel ID must start with 'C' (public), 'G' (private), or 'D' (DM) followed by at least 8 alphanumeric characters"
    )
    String channelId
) {}

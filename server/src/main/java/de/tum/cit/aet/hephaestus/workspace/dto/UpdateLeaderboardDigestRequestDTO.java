package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Whole weekly-digest configuration applied atomically: schedule (day/time) plus notification
 * settings (enabled/channel/team) in one transaction. Replaces the prior two sequential PATCHes,
 * which could leave the schedule changed but the channel not (or vice versa) on a mid-failure.
 */
@Schema(description = "Request to update the entire weekly leaderboard digest configuration atomically")
public record UpdateLeaderboardDigestRequestDTO(
    @NotNull(message = "Day cannot be null")
    @Min(value = 1, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    @Max(value = 7, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    @Schema(description = "Day of week (1=Monday, 7=Sunday)", minimum = "1", maximum = "7", example = "1")
    Integer day,

    @NotNull(message = "Time cannot be null")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Time must be in HH:mm format (00:00-23:59)")
    @Schema(description = "Time in 24-hour format (HH:mm)", example = "09:00")
    String time,

    @Schema(description = "Whether leaderboard notifications are enabled") Boolean enabled,

    @Schema(description = "Team name for filtering leaderboard notifications") String team,

    @Pattern(
        regexp = "^[CG][A-Z0-9]{8,}$",
        message = "Channel ID must start with 'C' (public) or 'G' (private) followed by at least 8 alphanumeric characters"
    )
    @Schema(description = "Slack channel ID for notifications", example = "C01234567AB")
    String channelId
) {}

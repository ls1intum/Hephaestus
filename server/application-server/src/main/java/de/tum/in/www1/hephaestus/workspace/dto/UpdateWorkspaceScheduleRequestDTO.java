package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating workspace leaderboard schedule configuration.
 * Day: 1=Monday, 2=Tuesday, ..., 7=Sunday
 * Time: Format "HH:mm" (24-hour format)
 */
@Schema(description = "Request to update the leaderboard notification schedule")
public record UpdateWorkspaceScheduleRequestDTO(
    @NotNull(message = "Day cannot be null")
    @Min(value = 1, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    @Max(value = 7, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    @Schema(
        description = "Day of week (1=Monday, 7=Sunday)",
        minimum = "1",
        maximum = "7",
        example = "1",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Integer day,

    @NotNull(message = "Time cannot be null")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Time must be in HH:mm format (00:00-23:59)")
    @Schema(
        description = "Time in 24-hour format (HH:mm)",
        example = "09:00",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String time
) {}

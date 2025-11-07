package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating workspace leaderboard schedule configuration.
 * Day: 1=Monday, 2=Tuesday, ..., 7=Sunday
 * Time: Format "HH:mm" (24-hour format)
 */
public record UpdateWorkspaceScheduleRequestDTO(
    @Min(value = 1, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    @Max(value = 7, message = "Day must be between 1 (Monday) and 7 (Sunday)")
    Integer day,
    
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Time must be in HH:mm format")
    String time
) {
}

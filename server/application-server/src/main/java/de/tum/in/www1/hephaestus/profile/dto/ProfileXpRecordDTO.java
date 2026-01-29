package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Profile-specific DTO representing the user's XP progress and level.
 *
 * <p>This DTO is used to transfer gamification metrics to the frontend, including:
 * <ul>
 *   <li>Current calculated level</li>
 *   <li>Progress within the current level (currentLevelXP)</li>
 *   <li>Threshold to reach the next level (xpNeeded)</li>
 *   <li>Lifetime accumulated XP (totalXP)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "User's XP and Level progress details")
public record ProfileXpRecordDTO(
    @Schema(description = "Current calculated level", example = "5") int currentLevel,
    @Schema(description = "XP accumulated in the current level", example = "450") long currentLevelXP,
    @Schema(description = "XP needed to reach the next level", example = "1000") long xpNeeded,
    @Schema(description = "Overall total XP accumulated", example = "5450") long totalXP
) {
    /**
     * Creates a minimal XP record with zero values (Level 1).
     *
     * @return a minimal ProfileXpRecordDTO representing 0 XP
     */
    public static ProfileXpRecordDTO empty() {
        return new ProfileXpRecordDTO(1, 0, 0, 0);
    }
}

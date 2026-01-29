package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

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
    @NonNull @Schema(description = "Current calculated level", example = "5") Integer currentLevel,
    @NonNull @Schema(description = "XP accumulated in the current level", example = "450") Long currentLevelXP,
    @NonNull @Schema(description = "XP needed to reach the next level", example = "1000") Long xpNeeded,
    @NonNull @Schema(description = "Overall total XP accumulated", example = "5450") Long totalXP
) {
    /**
     * Creates a minimal XP record with zero values (Level 1).
     *
     * @return a minimal ProfileXpRecordDTO representing 0 XP
     */
    public static ProfileXpRecordDTO empty() {
        return new ProfileXpRecordDTO(1, 0L, 0L, 0L);
    }
}

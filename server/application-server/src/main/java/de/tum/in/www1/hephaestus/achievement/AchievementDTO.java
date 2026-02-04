package de.tum.in.www1.hephaestus.achievement;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import org.springframework.lang.Nullable;

/**
 * DTO representing an achievement with user-specific progress.
 *
 * <p>This DTO is used to display achievements in the UI with:
 * <ul>
 *   <li>Static achievement metadata (name, description, icon)</li>
 *   <li>User-specific progress (current count vs required count)</li>
 *   <li>Status indicating if it's locked, available, or unlocked</li>
 * </ul>
 *
 * <p>Progress is calculated from activity event counts at query time,
 * while unlock status is stored in the UserAchievement table.
 */
@Schema(description = "Achievement with user-specific progress information")
public record AchievementDTO(
    @Schema(description = "Unique identifier for the achievement", example = "first_pull") String id,
    @Schema(description = "Human-readable name", example = "First Merge") String name,
    @Schema(description = "Description of how to earn the achievement", example = "Merge your first pull request")
    String description,
    @Schema(description = "Icon identifier for UI", example = "git-merge") String icon,
    @Schema(description = "Category for grouping achievements") AchievementCategory category,
    @Schema(description = "Visual level tier (1-7) for badge styling", example = "1") int level,
    @Nullable @Schema(description = "Parent achievement ID in progression chain", example = "null") String parentId,
    @Schema(description = "Current status for this user") AchievementStatus status,
    @Schema(description = "Current progress count (e.g., 4 PRs merged)", example = "4") long progress,
    @Schema(description = "Required count to unlock (e.g., 5 PRs)", example = "5") long maxProgress,
    @Nullable
    @Schema(description = "When the achievement was unlocked, null if not unlocked")
    Instant unlockedAt
) {
    /**
     * Creates an AchievementDTO from an AchievementType with progress information.
     *
     * @param type       the achievement type definition
     * @param progress   the user's current progress count
     * @param status     the computed status for this user
     * @param unlockedAt when the achievement was unlocked, or null if not unlocked
     * @return populated DTO
     */
    public static AchievementDTO fromType(AchievementType type, long progress, AchievementStatus status, Instant unlockedAt) {
        return new AchievementDTO(
            type.getId(),
            type.getName(),
            type.getDescription(),
            type.getIcon(),
            type.getCategory(),
            type.getLevel(),
            type.getParent() != null ? type.getParent().getId() : null,
            status,
            progress,
            type.getRequiredCount(),
            unlockedAt
        );
    }
}

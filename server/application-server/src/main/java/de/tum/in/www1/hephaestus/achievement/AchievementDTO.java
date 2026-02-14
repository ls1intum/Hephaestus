package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Optional;

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
 * <p>Progress is read directly from the {@link UserAchievement} progress table,
 * which is updated incrementally when activity events occur.
 */
@Schema(description = "Achievement with user-specific progress information")
public record AchievementDTO(
    @NonNull @Schema(description = "Unique identifier for the achievement", example = "first_pull") AchievementDefinition id,
//    @NonNull @Schema(description = "Human-readable name", example = "First Merge") String name,
//    @NonNull
//    @Schema(description = "Description of how to earn the achievement", example = "Merge your first pull request")
//    String description,
//    @NonNull @Schema(description = "Icon identifier for UI", example = "git-merge") String icon,
    @NonNull @Schema(description = "Category for grouping achievements", example = "pull_requests") AchievementCategory category,
    @NonNull
    @Schema(description = "Visual level tier/rarity for badge styling", example = "common")
    AchievementRarity rarity,
    @Nullable @Schema(description = "Parent achievement in progression chain", example = "first_pull") AchievementDefinition parent,
    @NonNull @Schema(description = "Current status of the achievement for this user", example = "unlocked") AchievementStatus status,
//    @NonNull @Schema(description = "Current progress count (e.g., 4 PRs merged)", example = "4") long progress,
//    @NonNull @Schema(description = "Required count to unlock (e.g., 5 PRs)", example = "5") long maxProgress,
    @NonNull
    @Schema(description = "The structured progress data based on the achievements evaluator")
    AchievementProgress progressData,
    @NonNull @Schema(description = "Optional of when the achievement was unlocked, empty() if not unlocked") Optional<Instant> unlockedAt
) {
    /**
     * Creates an AchievementDTO from an AchievementDefinition with progress information.
     *
     * @param definition   the achievement definition
     * @param progressData the data associated with this achievements progress
     * @param status       the computed status for this user
     * @param unlockedAt   Optional of when the achievement was unlocked, or {@link Optional#empty()} if not unlocked
     * @return populated DTO
     */
    public static AchievementDTO fromDefinition(
        AchievementDefinition definition,
        AchievementStatus status,
        AchievementProgress progressData,
        Optional<Instant> unlockedAt
    ) {
        return new AchievementDTO(
            definition,
//            definition.getName(),
//            definition.getDescription(),
//            definition.getIcon(),
            definition.getCategory(),
            definition.getRarity(),
            definition.getParent(),
            status,
            progressData,
            unlockedAt
        );
    }
}

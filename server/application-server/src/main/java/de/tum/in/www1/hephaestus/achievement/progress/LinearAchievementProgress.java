package de.tum.in.www1.hephaestus.achievement.progress;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LinearAchievementProgress", description = "Linear progress with current and target counts")
public record LinearAchievementProgress(
    int current,
    int target
) implements AchievementProgress {
    public LinearAchievementProgress(int target) {
        this(0, target);
    }
}

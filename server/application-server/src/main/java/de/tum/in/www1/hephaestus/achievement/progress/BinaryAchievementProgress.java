package de.tum.in.www1.hephaestus.achievement.progress;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BinaryAchievementProgress", description = "Binary progress indicating unlocked state")
public record BinaryAchievementProgress(
    boolean unlocked
) implements AchievementProgress {}

package de.tum.in.www1.hephaestus.achievement.progress;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(name = "LinearAchievementProgress", description = "Linear progress with current and target counts")
public record LinearAchievementProgress(
    @PositiveOrZero
    @JsonProperty(required = true)
    int current,

    @PositiveOrZero
    @JsonProperty(required = true)
    int target
) implements AchievementProgress {
    public LinearAchievementProgress(int target) {
        this(0, target);
    }
}

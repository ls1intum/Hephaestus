package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.AchievementDefinition;
import de.tum.in.www1.hephaestus.achievement.AchievementService;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default achievement evaluator that increments progress by one.
 *
 * <p>This is the standard strategy: each qualifying activity event adds
 * exactly 1 to the achievement's progress {@code "current"} value. Most achievements
 * (e.g., "Merge N pull requests") use this simple counting approach.
 *
 * <p>The target count is read from the {@link AchievementDefinition} parameters
 * map under the {@code "count"} key. The "count" key should contain an {@link Integer}.
 *
 * <p>Registered as a Spring {@link Component} so that {@link AchievementService}
 * can resolve it from the evaluator strategy map at runtime.
 *
 * @see AchievementEvaluator
 * @see AchievementDefinition#getEvaluatorClass()
 */
@Slf4j
@Component
public class StandardCountEvaluator implements AchievementEvaluator {

    @Override
    public boolean updateProgress(UserAchievement userAchievement) {
        AchievementDefinition def = userAchievement.getAchievementDefinition();
        Integer count = (Integer) def.getParameters().get("count");
        if (count == null) {
            log.debug("Configuration parameter missmatch for Achievement: {}. Parameter \"count\" was null but should exist as Integer!", def);
            return false;
        }

        // Only increment if the current value has not yet reached the count threshold
        int current = (Integer) userAchievement.getProgressData().getOrDefault("current", 0);
        if (current < count)
            current++;
        boolean wasUnlocked = current == count;
        Map<String, Object> progressData = Map.of(
            "current", current,
            "count", count
        );
        userAchievement.setProgressData(progressData);
        return wasUnlocked;
    }
}

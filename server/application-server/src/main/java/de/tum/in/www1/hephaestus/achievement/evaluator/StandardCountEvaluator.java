package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.AchievementDefinition;
import de.tum.in.www1.hephaestus.achievement.AchievementService;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import org.springframework.stereotype.Component;

/**
 * Default achievement evaluator that increments progress by one.
 *
 * <p>This is the standard strategy: each qualifying activity event adds
 * exactly 1 to the achievement's {@code currentValue}. Most achievements
 * (e.g., "Merge N pull requests") use this simple counting approach.
 *
 * <p>The target count is read from the {@link AchievementDefinition} parameters
 * map under the {@code "count"} key. The unlock threshold check is performed
 * by {@link AchievementService}, not here. // TODO: BUT IT SHOULD BE PERFORMED HERE!!!
 *
 * <p>Registered as a Spring {@link Component} so that {@link AchievementService}
 * can resolve it from the evaluator strategy map at runtime.
 *
 * @see AchievementEvaluator
 * @see AchievementDefinition#getEvaluatorClass()
 */
@Component
public class StandardCountEvaluator implements AchievementEvaluator {

    @Override
    public void updateProgress(UserAchievement userAchievement, ActivityEventType eventType) {
        AchievementDefinition def = userAchievement.getAchievementDefinition();
        Number countVal = (Number) def.getParameters().get("count");
        long target = countVal != null ? countVal.longValue() : 0;

        // Only increment if the current value has not yet reached the target
        if (userAchievement.getCurrentValue() < target) {
            userAchievement.setCurrentValue(userAchievement.getCurrentValue() + 1);
        }
    }
}

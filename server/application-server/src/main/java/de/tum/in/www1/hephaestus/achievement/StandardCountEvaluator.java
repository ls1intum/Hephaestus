package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEventType;

/**
 * Default achievement evaluator that increments progress by one.
 *
 * <p>This is the standard strategy: each qualifying activity event adds
 * exactly 1 to the achievement's {@code currentValue}. Most achievements
 * (e.g., "Merge N pull requests") use this simple counting approach.
 *
 * @see AchievementEvaluator
 */
public class StandardCountEvaluator implements AchievementEvaluator {

    @Override
    public void updateProgress(UserAchievement userAchievement, ActivityEventType eventType) {
        userAchievement.setCurrentValue(userAchievement.getCurrentValue() + 1);
    }
}

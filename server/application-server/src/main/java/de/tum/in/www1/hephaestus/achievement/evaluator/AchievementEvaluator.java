package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.AchievementService;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;

/**
 * Strategy interface for updating achievement progress.
 *
 * <p>Implementations define how an achievement's {@code currentValue} should
 * be adjusted when a qualifying activity event occurs. The default strategy
 * ({@link StandardCountEvaluator}) simply increments by 1, but custom
 * implementations could apply multipliers, weighted scoring, or other logic.
 *
 * @see StandardCountEvaluator
 * @see AchievementService
 */
public interface AchievementEvaluator {
    /**
     * Update the progress on a user achievement in response to an activity event.
     *
     * <p>Implementations should modify {@link UserAchievement#setCurrentValue(long)}
     * as appropriate. The caller is responsible for checking the unlock threshold
     * and persisting the entity.
     *
     * @param userAchievement the achievement progress record to update
     * @param eventType the activity event type that triggered the evaluation
     */
    void updateProgress(UserAchievement userAchievement, ActivityEventType eventType);
}

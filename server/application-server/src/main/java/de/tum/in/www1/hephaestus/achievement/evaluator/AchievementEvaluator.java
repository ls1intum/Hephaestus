package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.AchievementService;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;

/**
 * Strategy interface for updating achievement progress.
 *
 * <p>Implementations define how an achievement's {@code current} value should
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
     * <p>Implementations should replace the existing progress record with the updated record of type {@link AchievementProgress}
     * The caller is responsible for persisting the entity.
     *
     * @param userAchievement the achievement progress record to update
     * @return {@code true} if the achievement was unlocked during update, else {@code false}
     * @see AchievementProgress
     * @see LinearAchievementProgress
     */
    boolean updateProgress(UserAchievement userAchievement);
}

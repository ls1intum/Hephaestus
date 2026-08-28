package de.tum.cit.aet.hephaestus.achievement.evaluator;

import de.tum.cit.aet.hephaestus.achievement.AchievementDefinition;
import de.tum.cit.aet.hephaestus.achievement.UserAchievement;
import de.tum.cit.aet.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.cit.aet.hephaestus.activity.ActivitySavedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simple evaluator that increments a {@link LinearAchievementProgress} by 1.
 *
 * <p>Expects {@link UserAchievement#getProgressData()} to be a
 * {@link LinearAchievementProgress}. If the type differs the evaluator logs a
 * warning and returns {@code false}. Returns {@code true} when the update
 * causes the achievement to unlock (current == target).
 *
 * @see AchievementEvaluator
 * @see AchievementDefinition#evaluatorClass()
 */
@Slf4j
@Component
public class StandardCountEvaluator implements AchievementEvaluator {

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        if (!(userAchievement.getProgressData() instanceof LinearAchievementProgress(int current, int target))) {
            log.warn(
                "Expected LinearAchievementProgress but received {} for achievement: {}",
                userAchievement.getProgressData(),
                userAchievement.getAchievementId()
            );
            return false;
        }

        if (current < target) current++;
        boolean wasUnlocked = current == target;
        userAchievement.setProgressData(new LinearAchievementProgress(current, target));
        return wasUnlocked;
    }
}

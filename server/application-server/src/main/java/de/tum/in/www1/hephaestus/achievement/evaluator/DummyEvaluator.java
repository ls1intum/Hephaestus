package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * This evaluator is intended to use in all achievement definitions that do not have a correct evaluator yet.
 * It simply logs a debug message that this achievement is not yet processed correctly.
 */
@Slf4j
@Component
public class DummyEvaluator implements AchievementEvaluator {

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        var user = userAchievement.getUser();
        log.debug(
            "Evaluation of achievement: {} for user: {} for event: {} skipped by DummyEvaluator! No corresponding Evaluator available yet!",
            userAchievement.getAchievementId(),
            user != null ? LoggingUtils.sanitizeForLog(user.getLogin()) : "unknown",
            event.eventType()
        );
        return false;
    }
}

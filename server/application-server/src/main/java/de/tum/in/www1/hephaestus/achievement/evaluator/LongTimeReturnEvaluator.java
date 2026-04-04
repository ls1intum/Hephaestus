package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "The Ancient One" achievement:
 * return to activity after more than 90 days of inactivity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongTimeReturnEvaluator implements AchievementEvaluator {

    private static final Duration MIN_ABSENCE = Duration.ofDays(90);

    private final ActivityEventRepository activityEventRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Long actorId = userAchievement.getUser().getId();

        return activityEventRepository
            .findMaxOccurredAtByActorIdBefore(actorId, event.occurredAt())
            .map(previousActivity -> {
                Duration gap = Duration.between(previousActivity, event.occurredAt());
                if (gap.compareTo(MIN_ABSENCE) >= 0) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Brute Force" achievement: 5 commits within a 5-minute window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BruteForceEvaluator implements AchievementEvaluator {

    private static final long REQUIRED_COMMITS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final ActivityEventRepository activityEventRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Long actorId = userAchievement.getUser().getId();
        Instant windowStart = event.occurredAt().minus(WINDOW);
        Instant windowEnd = event.occurredAt(); // inclusive of current event (handled by repository query semantics)

        long count = activityEventRepository.countByActorIdAndEventTypeInWindow(
            actorId,
            ActivityEventType.COMMIT_CREATED.name(),
            windowStart,
            windowEnd
        );

        if (count >= REQUIRED_COMMITS) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }
}

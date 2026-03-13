package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Night Owl" achievement:
 * 5 commits between 01:00 and 05:00 UTC on the same calendar day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightOwlEvaluator implements AchievementEvaluator {

    private static final long REQUIRED_NIGHT_COMMITS = 5;
    private static final LocalTime NIGHT_START = LocalTime.of(1, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(5, 0);

    private final ActivityEventRepository activityEventRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        // Only check if current event is in the night window
        LocalTime eventTime = event.occurredAt().atZone(ZoneOffset.UTC).toLocalTime();
        if (eventTime.isBefore(NIGHT_START) || !eventTime.isBefore(NIGHT_END)) {
            return false;
        }

        Long actorId = userAchievement.getUser().getId();
        LocalDate eventDate = event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        Instant windowStart = eventDate.atTime(NIGHT_START).toInstant(ZoneOffset.UTC);
        Instant windowEnd = eventDate.atTime(NIGHT_END).toInstant(ZoneOffset.UTC);

        long count = activityEventRepository.countByActorIdAndEventTypeInWindow(
            actorId,
            ActivityEventType.COMMIT_CREATED.name(),
            windowStart,
            windowEnd
        );

        if (count >= REQUIRED_NIGHT_COMMITS) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }
}

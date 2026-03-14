package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Necromancer" achievement:
 * Close an issue that has been open for over 6 months.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NecromancerEvaluator implements AchievementEvaluator {

    private final IssueRepository issueRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return issueRepository
            .findById(event.targetId())
            .map(issue -> {
                Instant createdAt = issue.getCreatedAt();
                if (createdAt == null) {
                    return false;
                }

                // calculate duration between creation and the event's occurrence (closure)
                Instant closedAt = event.occurredAt();
                ZonedDateTime created = createdAt.atZone(ZoneOffset.UTC);
                ZonedDateTime closed = closedAt.atZone(ZoneOffset.UTC);

                long months = ChronoUnit.MONTHS.between(created, closed);
                if (months >= 6) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

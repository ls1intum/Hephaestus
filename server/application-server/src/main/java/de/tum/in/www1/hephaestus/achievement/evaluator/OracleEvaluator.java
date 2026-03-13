package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Oracle" achievement:
 * close an issue that was open for more than 6 months.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleEvaluator implements AchievementEvaluator {

    private static final Duration MIN_OPEN_DURATION = Duration.ofDays(180);

    private final IssueRepository issueRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return issueRepository
            .findById(event.targetId())
            .map(issue -> {
                if (issue.getCreatedAt() == null || issue.getClosedAt() == null) {
                    return false;
                }
                Duration openDuration = Duration.between(issue.getCreatedAt(), issue.getClosedAt());
                if (openDuration.compareTo(MIN_OPEN_DURATION) > 0) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

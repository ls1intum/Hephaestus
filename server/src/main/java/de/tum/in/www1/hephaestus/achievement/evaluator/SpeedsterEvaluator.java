package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Speedster" achievement: merge a PR within 5 minutes of opening.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeedsterEvaluator implements AchievementEvaluator {

    private static final Duration MAX_MERGE_TIME = Duration.ofMinutes(5);

    private final PullRequestRepository pullRequestRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return pullRequestRepository
            .findById(event.targetId())
            .map(pr -> {
                if (pr.getCreatedAt() == null || pr.getMergedAt() == null) {
                    return false;
                }
                Duration mergeTime = Duration.between(pr.getCreatedAt(), pr.getMergedAt());
                if (!mergeTime.isNegative() && mergeTime.compareTo(MAX_MERGE_TIME) <= 0) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

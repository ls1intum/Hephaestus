package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Itsy Bitsy" achievement: make a commit that changes exactly 1 line.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItsyBitsyEvaluator implements AchievementEvaluator {

    private final CommitRepository commitRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return commitRepository
            .findById(event.targetId())
            .map(commit -> {
                int totalChanges = commit.getAdditions() + commit.getDeletions();
                if (totalChanges == 1) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

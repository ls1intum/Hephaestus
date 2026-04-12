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
 *
 * <p>A single-line modification in git is represented as 1 addition + 1 deletion.
 * Without parsing the actual diff we cannot verify it's the exact same line,
 * but {@code changedFiles == 1} with at most 1 add + 1 delete is overwhelmingly
 * a single-line edit. Also covers pure adds (1+0) and pure deletes (0+1).
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
                int additions = commit.getAdditions();
                int deletions = commit.getDeletions();
                boolean itsyBitsy =
                    commit.getChangedFiles() == 1 && additions <= 1 && deletions <= 1 && (additions + deletions) > 0;

                if (itsyBitsy) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

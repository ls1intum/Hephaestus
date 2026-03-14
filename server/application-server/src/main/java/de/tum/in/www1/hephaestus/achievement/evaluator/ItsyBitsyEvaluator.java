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

        // Currently false implementation of the achievement evaluator for Itsy Bitsy that cannot be resolved due to technical limitations
        return false;

//        return commitRepository
//            .findById(event.targetId())
//            .map(commit -> {
//                int totalChanges = commit.getAdditions() + commit.getDeletions();
//                if (totalChanges == 1) {
//                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
//                    return true;
//                }
//                return false;
//            })
//            .orElse(false);
    }

    /**
     * Helper method to resolve if a git diff patch String shows only a one and the same line modification in a file
     * which is technically one deletion (-) followed by one addition (+) of the same line in the git diff string
     * @param patch The actual git diff change text to parse for checking only one line modification
     * @return true when only one and the same line was changed
     */
    private boolean isExactSingleLineModification(String patch) {
        if (patch == null || patch.isEmpty()) {
            return false;
        }

        int additions = 0;
        int deletions = 0;
        boolean isAdjacent = false;

        String[] lines = patch.split("\n");

        for (int i = 0; i < lines.length; i++) {
            // Ignore file headers
            if (lines[i].startsWith("---") || lines[i].startsWith("+++")) {
                continue;
            }

            if (lines[i].startsWith("-")) {
                deletions++;
                // Check if the very next line is an addition
                if (i + 1 < lines.length && lines[i + 1].startsWith("+")) {
                    isAdjacent = true;
                }
            } else if (lines[i].startsWith("+")) {
                additions++;
                // Check if the very next line is a deletion (some git clients reverse order)
                if (i + 1 < lines.length && lines[i + 1].startsWith("-")) {
                    isAdjacent = true;
                }
            }
        }

        // Must be exactly 1 of each, and they must be touching
        return additions == 1 && deletions == 1 && isAdjacent;
    }
}

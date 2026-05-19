package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Atomic Reconstruction" achievement:
 * 10 consecutive commits each changing at most 3 lines in at most 2 files.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtomicChangesEvaluator implements AchievementEvaluator {

    private static final int REQUIRED_CONSECUTIVE = 10;
    private static final int MAX_LINES = 3;
    private static final int MAX_FILES = 2;

    private final CommitRepository commitRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Long authorId = userAchievement.getUser().getId();
        List<Commit> recentCommits = commitRepository.findTopNByAuthorIdOrderByAuthoredAtDesc(
            authorId,
            event.occurredAt(),
            PageRequest.of(0, REQUIRED_CONSECUTIVE)
        );

        if (recentCommits.size() < REQUIRED_CONSECUTIVE) {
            return false;
        }

        boolean allAtomic = recentCommits.stream().allMatch(this::isAtomicCommit);
        if (allAtomic) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }

    private boolean isAtomicCommit(Commit commit) {
        int totalLines = commit.getAdditions() + commit.getDeletions();
        return totalLines <= MAX_LINES && commit.getChangedFiles() <= MAX_FILES;
    }
}

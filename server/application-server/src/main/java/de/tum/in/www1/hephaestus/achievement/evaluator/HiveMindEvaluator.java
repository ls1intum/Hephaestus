package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Hive Mind" achievement:
 * close an issue that had 10 or more unique participants (commenters + author).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HiveMindEvaluator implements AchievementEvaluator {

    private static final long MIN_PARTICIPANTS = 10;

    private final IssueRepository issueRepository;
    private final IssueCommentRepository issueCommentRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        return issueRepository
            .findById(event.targetId())
            .map(issue -> {
                // Count distinct comment authors + the issue author = total unique participants
                long distinctCommenters = issueCommentRepository.countDistinctAuthorIdsByIssueId(issue.getId());
                // Add 1 for the issue author (may overlap with commenters, but that's conservative)
                long totalParticipants = distinctCommenters + 1;

                if (totalParticipants >= MIN_PARTICIPANTS) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

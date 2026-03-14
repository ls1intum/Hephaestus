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
 * close an issue that had 10 or more unique participants.
 *
 * Note: We use the number of distinct issue commenters as a conservative proxy
 * for unique participants to avoid double-counting the issue author when they
 * also commented on the issue.
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
                // Count distinct comment authors; use this as the participant count to avoid
                // double-counting the issue author when they also commented.
                long distinctCommenters = issueCommentRepository.countDistinctAuthorIdsByIssueId(issue.getId());
                long totalParticipants = distinctCommenters;

                if (totalParticipants >= MIN_PARTICIPANTS) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

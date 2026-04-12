package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
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

    private final IssueCommentRepository issueCommentRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        // Uses UNION of comment authors and issue author to avoid double-counting
        // when the issue author also commented.
        long totalParticipants = issueCommentRepository.countDistinctParticipantsByIssueId(event.targetId());

        if (totalParticipants >= MIN_PARTICIPANTS) {
            userAchievement.setProgressData(new BinaryAchievementProgress(true));
            return true;
        }
        return false;
    }
}

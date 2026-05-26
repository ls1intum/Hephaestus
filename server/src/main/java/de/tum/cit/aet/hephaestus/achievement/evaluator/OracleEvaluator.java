package de.tum.cit.aet.hephaestus.achievement.evaluator;

import de.tum.cit.aet.hephaestus.achievement.UserAchievement;
import de.tum.cit.aet.hephaestus.achievement.progress.BinaryAchievementProgress;
import de.tum.cit.aet.hephaestus.activity.ActivitySavedEvent;
import de.tum.cit.aet.hephaestus.integration.scm.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.issuecomment.IssueCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator for the "Oracle" achievement:
 * Close your own issue that had zero interaction from others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleEvaluator implements AchievementEvaluator {

    private final IssueRepository issueRepository;
    private final IssueCommentRepository issueCommentRepository;

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (userAchievement.getUnlockedAt() != null) {
            return false;
        }

        Long userId = userAchievement.getUser().getId();

        return issueRepository
            .findById(event.targetId())
            .map(issue -> {
                // Must be the issue author
                if (issue.getAuthor() == null || !issue.getAuthor().getId().equals(userId)) {
                    return false;
                }
                // Must have zero comments from others
                long othersComments = issueCommentRepository.countByIssueIdAndAuthorIdNot(issue.getId(), userId);
                if (othersComments == 0) {
                    userAchievement.setProgressData(new BinaryAchievementProgress(true));
                    return true;
                }
                return false;
            })
            .orElse(false);
    }
}

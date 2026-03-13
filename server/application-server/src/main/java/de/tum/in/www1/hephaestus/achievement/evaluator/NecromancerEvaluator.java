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
 * Evaluator for the "Necromancer" achievement:
 * close your own issue that had zero interaction from others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NecromancerEvaluator implements AchievementEvaluator {

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

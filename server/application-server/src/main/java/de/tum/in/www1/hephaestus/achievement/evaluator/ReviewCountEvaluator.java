package de.tum.in.www1.hephaestus.achievement.evaluator;

import de.tum.in.www1.hephaestus.achievement.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluator that increments a {@link LinearAchievementProgress} by 1,
 * specifically intended for reviews on PRs that are not authored by oneself.
 */
@Slf4j
@Component
public class ReviewCountEvaluator implements AchievementEvaluator {

    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;

    public ReviewCountEvaluator(
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewCommentRepository reviewCommentRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
    }

    @Override
    public boolean updateProgress(UserAchievement userAchievement, ActivitySavedEvent event) {
        if (!(userAchievement.getProgressData() instanceof LinearAchievementProgress(int current, int target))) {
            log.warn(
                "Expected LinearAchievementProgress but received {} for achievement: {}",
                userAchievement.getProgressData(),
                userAchievement.getAchievementId()
            );
            return false;
        }

        boolean isNotAuthoredByOneself = false;

        if (event.targetType() == ActivityTargetType.REVIEW) {
            isNotAuthoredByOneself = reviewRepository
                .findById(event.targetId())
                .map(review -> {
                    if (review.getPullRequest() == null || review.getPullRequest().getAuthor() == null)
                        return true;
                    if (event.user().isEmpty())
                        return false;
                    return !review.getPullRequest().getAuthor().getId().equals(event.user().get().getId());
                })
                .orElse(false);
        } else if (event.targetType() == ActivityTargetType.REVIEW_COMMENT) {
            isNotAuthoredByOneself = reviewCommentRepository
                .findById(event.targetId())
                .map(comment -> {
                    if (comment.getPullRequest() == null || comment.getPullRequest().getAuthor() == null)
                        return true;
                    if (event.user().isEmpty())
                        return false;
                    return !comment.getPullRequest().getAuthor().getId().equals(event.user().get().getId());
                })
                .orElse(false);
        }

        if (isNotAuthoredByOneself) {
            if (current < target) {
                current++;
            }
        }

        boolean wasUnlocked = current == target;
        userAchievement.setProgressData(new LinearAchievementProgress(current, target));
        return wasUnlocked;
    }
}

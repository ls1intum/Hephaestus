package de.tum.in.www1.hephaestus.achievement.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link ReviewCountEvaluator}.
 */
class ReviewCountEvaluatorTest extends BaseUnitTest {

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    private ReviewCountEvaluator evaluator;
    private User reviewer;
    private User prAuthor;

    @BeforeEach
    void setUp() {
        evaluator = new ReviewCountEvaluator(reviewRepository, reviewCommentRepository);

        reviewer = new User();
        reviewer.setId(1L);
        reviewer.setLogin("reviewer");

        prAuthor = new User();
        prAuthor.setId(2L);
        prAuthor.setLogin("author");
    }

    private ActivitySavedEvent createReviewEvent(Long targetId) {
        return new ActivitySavedEvent(
            Optional.of(reviewer),
            ActivityEventType.REVIEW_APPROVED,
            Instant.now(),
            1L,
            ActivityTargetType.REVIEW,
            targetId
        );
    }

    private UserAchievement createUserAchievement(int current, int target) {
        return UserAchievement.builder()
            .user(reviewer)
            .achievementId("review.common.1")
            .progressData(new LinearAchievementProgress(current, target))
            .build();
    }

    private PullRequestReview createReview(User pullRequestAuthor) {
        PullRequestReview review = new PullRequestReview();
        PullRequest pr = new PullRequest();
        pr.setAuthor(pullRequestAuthor);
        review.setPullRequest(pr);
        return review;
    }

    @Nested
    @DisplayName("Non-Self Review")
    class NonSelfReviewTests {

        @Test
        @DisplayName("increments progress when reviewing someone else's PR")
        void incrementsForNonSelfReview() {
            Long reviewId = 100L;
            when(reviewRepository.findByIdWithPullRequestAuthor(reviewId)).thenReturn(
                Optional.of(createReview(prAuthor))
            );

            UserAchievement ua = createUserAchievement(0, 10);
            boolean result = evaluator.updateProgress(ua, createReviewEvent(reviewId));

            assertThat(result).isFalse();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns true when reaching target from non-self review")
        void unlocksAtTarget() {
            Long reviewId = 100L;
            when(reviewRepository.findByIdWithPullRequestAuthor(reviewId)).thenReturn(
                Optional.of(createReview(prAuthor))
            );

            UserAchievement ua = createUserAchievement(9, 10);
            boolean result = evaluator.updateProgress(ua, createReviewEvent(reviewId));

            assertThat(result).isTrue();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Self Review Filtering")
    class SelfReviewTests {

        @Test
        @DisplayName("does not increment for self-review")
        void doesNotIncrementForSelfReview() {
            Long reviewId = 100L;
            when(reviewRepository.findByIdWithPullRequestAuthor(reviewId)).thenReturn(
                Optional.of(createReview(reviewer))
            ); // Same user as reviewer

            UserAchievement ua = createUserAchievement(5, 10);
            boolean result = evaluator.updateProgress(ua, createReviewEvent(reviewId));

            assertThat(result).isFalse();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(5); // Unchanged
        }

        @Test
        @DisplayName("does not increment when review not found")
        void doesNotIncrementWhenReviewNotFound() {
            Long reviewId = 999L;
            when(reviewRepository.findByIdWithPullRequestAuthor(reviewId)).thenReturn(Optional.empty());

            UserAchievement ua = createUserAchievement(5, 10);
            boolean result = evaluator.updateProgress(ua, createReviewEvent(reviewId));

            assertThat(result).isFalse();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(5); // Unchanged
        }
    }
}

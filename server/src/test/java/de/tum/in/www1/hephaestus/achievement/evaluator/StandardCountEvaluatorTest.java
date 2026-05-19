package de.tum.in.www1.hephaestus.achievement.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.achievement.UserAchievement;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StandardCountEvaluator}.
 */
class StandardCountEvaluatorTest extends BaseUnitTest {

    private StandardCountEvaluator evaluator;
    private User testUser;

    @BeforeEach
    void setUp() {
        evaluator = new StandardCountEvaluator();
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
    }

    private ActivitySavedEvent createEvent(ActivityEventType eventType) {
        return new ActivitySavedEvent(
            Optional.of(testUser),
            eventType,
            Instant.now(),
            1L,
            ActivityTargetType.PULL_REQUEST,
            100L
        );
    }

    private UserAchievement createUserAchievement(int current, int target) {
        return UserAchievement.builder()
            .user(testUser)
            .achievementId("test.achievement")
            .progressData(new LinearAchievementProgress(current, target))
            .build();
    }

    @Nested
    @DisplayName("Progress Increment")
    class ProgressIncrementTests {

        @Test
        @DisplayName("increments current from 0 to 1")
        void incrementsFromZero() {
            UserAchievement ua = createUserAchievement(0, 5);
            evaluator.updateProgress(ua, createEvent(ActivityEventType.PULL_REQUEST_MERGED));

            assertThat(ua.getProgressData()).isInstanceOf(LinearAchievementProgress.class);
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(1);
            assertThat(progress.target()).isEqualTo(5);
        }

        @Test
        @DisplayName("increments current by 1 each call")
        void incrementsByOne() {
            UserAchievement ua = createUserAchievement(3, 10);
            evaluator.updateProgress(ua, createEvent(ActivityEventType.COMMIT_CREATED));

            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(4);
        }

        @Test
        @DisplayName("does not increment past target")
        void doesNotIncrementPastTarget() {
            UserAchievement ua = createUserAchievement(5, 5);
            evaluator.updateProgress(ua, createEvent(ActivityEventType.COMMIT_CREATED));

            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(5); // Not incremented beyond target
        }
    }

    @Nested
    @DisplayName("Unlock Detection")
    class UnlockDetectionTests {

        @Test
        @DisplayName("returns true when reaching target")
        void returnsTrueAtTarget() {
            UserAchievement ua = createUserAchievement(4, 5);
            boolean result = evaluator.updateProgress(ua, createEvent(ActivityEventType.PULL_REQUEST_MERGED));

            assertThat(result).isTrue();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(5);
        }

        @Test
        @DisplayName("returns false when not yet at target")
        void returnsFalseBeforeTarget() {
            UserAchievement ua = createUserAchievement(2, 5);
            boolean result = evaluator.updateProgress(ua, createEvent(ActivityEventType.PULL_REQUEST_MERGED));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true for target of 1 from fresh progress")
        void unlocksSingleTargetAchievement() {
            UserAchievement ua = createUserAchievement(0, 1);
            boolean result = evaluator.updateProgress(ua, createEvent(ActivityEventType.ISSUE_CREATED));

            assertThat(result).isTrue();
            LinearAchievementProgress progress = (LinearAchievementProgress) ua.getProgressData();
            assertThat(progress.current()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("returns false for wrong progress type")
        void returnsFalseForWrongProgressType() {
            UserAchievement ua = UserAchievement.builder()
                .user(testUser)
                .achievementId("test.achievement")
                .progressData(new de.tum.in.www1.hephaestus.achievement.progress.BinaryAchievementProgress())
                .build();

            boolean result = evaluator.updateProgress(ua, createEvent(ActivityEventType.COMMIT_CREATED));
            assertThat(result).isFalse();
        }
    }
}

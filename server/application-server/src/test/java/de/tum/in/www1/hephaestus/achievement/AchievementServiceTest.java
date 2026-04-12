package de.tum.in.www1.hephaestus.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.achievement.evaluator.AchievementEvaluator;
import de.tum.in.www1.hephaestus.achievement.evaluator.StandardCountEvaluator;
import de.tum.in.www1.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.cache.CacheManager;

/**
 * Unit tests for {@link AchievementService}.
 */
class AchievementServiceTest extends BaseUnitTest {

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private AchievementRegistry achievementRegistry;

    @Mock
    private CacheManager cacheManager;

    private AchievementService service;
    private User testUser;
    private StandardCountEvaluator standardCountEvaluator;

    @BeforeEach
    void setUp() {
        standardCountEvaluator = new StandardCountEvaluator();
        List<AchievementEvaluator> evaluators = List.of(standardCountEvaluator);

        service = new AchievementService(userAchievementRepository, evaluators, achievementRegistry, cacheManager);
        service.initEvaluatorMap();

        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
    }

    private ActivitySavedEvent createEvent(ActivityEventType eventType) {
        return new ActivitySavedEvent(
            Optional.of(testUser),
            eventType,
            Instant.parse("2024-08-15T10:00:00Z"),
            1L,
            ActivityTargetType.PULL_REQUEST,
            100L
        );
    }

    @Nested
    @DisplayName("checkAndUnlock")
    class CheckAndUnlockTests {

        @Test
        @DisplayName("skips when user is empty")
        void skipsWhenUserIsEmpty() {
            ActivitySavedEvent event = new ActivitySavedEvent(
                Optional.empty(),
                ActivityEventType.PULL_REQUEST_MERGED,
                Instant.now(),
                1L,
                ActivityTargetType.PULL_REQUEST,
                100L
            );

            List<AchievementDefinition> result = service.checkAndUnlock(event);

            assertThat(result).isEmpty();
            verifyNoInteractions(userAchievementRepository);
        }

        @Test
        @DisplayName("skips when no achievements triggered by event type")
        void skipsWhenNoCandidates() {
            when(achievementRegistry.getByTriggerEvent(ActivityEventType.PULL_REQUEST_MERGED)).thenReturn(List.of());

            List<AchievementDefinition> result = service.checkAndUnlock(
                createEvent(ActivityEventType.PULL_REQUEST_MERGED)
            );

            assertThat(result).isEmpty();
            verify(userAchievementRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates new UserAchievement and increments progress")
        void createsNewProgressRecord() {
            AchievementDefinition def = new AchievementDefinition(
                "commit.common.1",
                AchievementCategory.COMMITS,
                AchievementRarity.COMMON,
                new LinearAchievementProgress(0, 1),
                null,
                false,
                Set.of(ActivityEventType.COMMIT_CREATED),
                "StandardCountEvaluator"
            );

            when(achievementRegistry.getByTriggerEvent(ActivityEventType.COMMIT_CREATED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());

            List<AchievementDefinition> result = service.checkAndUnlock(createEvent(ActivityEventType.COMMIT_CREATED));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo("commit.common.1");
            verify(userAchievementRepository).save(any(UserAchievement.class));
        }

        @Test
        @DisplayName("skips already unlocked achievements")
        void skipsAlreadyUnlocked() {
            AchievementDefinition def = new AchievementDefinition(
                "pr.merged.common.1",
                AchievementCategory.PULL_REQUESTS,
                AchievementRarity.COMMON,
                new LinearAchievementProgress(0, 1),
                null,
                false,
                Set.of(ActivityEventType.PULL_REQUEST_MERGED),
                "StandardCountEvaluator"
            );

            UserAchievement existingProgress = UserAchievement.builder()
                .user(testUser)
                .achievementId("pr.merged.common.1")
                .progressData(new LinearAchievementProgress(1, 1))
                .unlockedAt(Instant.parse("2024-07-01T00:00:00Z"))
                .build();

            when(achievementRegistry.getByTriggerEvent(ActivityEventType.PULL_REQUEST_MERGED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(
                List.of(existingProgress)
            );

            List<AchievementDefinition> result = service.checkAndUnlock(
                createEvent(ActivityEventType.PULL_REQUEST_MERGED)
            );

            assertThat(result).isEmpty();
            verify(userAchievementRepository, never()).save(any());
        }

        @Test
        @DisplayName("increments existing progress without unlocking")
        void incrementsExistingProgress() {
            AchievementDefinition def = new AchievementDefinition(
                "pr.merged.rare",
                AchievementCategory.PULL_REQUESTS,
                AchievementRarity.RARE,
                new LinearAchievementProgress(0, 15),
                "pr.merged.uncommon",
                false,
                Set.of(ActivityEventType.PULL_REQUEST_MERGED),
                "StandardCountEvaluator"
            );

            UserAchievement existingProgress = UserAchievement.builder()
                .user(testUser)
                .achievementId("pr.merged.rare")
                .progressData(new LinearAchievementProgress(5, 15))
                .build();

            when(achievementRegistry.getByTriggerEvent(ActivityEventType.PULL_REQUEST_MERGED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(
                List.of(existingProgress)
            );

            List<AchievementDefinition> result = service.checkAndUnlock(
                createEvent(ActivityEventType.PULL_REQUEST_MERGED)
            );

            assertThat(result).isEmpty(); // Not unlocked yet
            verify(userAchievementRepository).save(existingProgress);
            LinearAchievementProgress progress = (LinearAchievementProgress) existingProgress.getProgressData();
            assertThat(progress.current()).isEqualTo(6);
        }

        @Test
        @DisplayName("sets unlockedAt to event's occurredAt timestamp")
        void setsUnlockedAtToEventTimestamp() {
            Instant historicalTimestamp = Instant.parse("2024-07-15T14:30:00Z");
            AchievementDefinition def = new AchievementDefinition(
                "commit.common.1",
                AchievementCategory.COMMITS,
                AchievementRarity.COMMON,
                new LinearAchievementProgress(0, 1),
                null,
                false,
                Set.of(ActivityEventType.COMMIT_CREATED),
                "StandardCountEvaluator"
            );

            when(achievementRegistry.getByTriggerEvent(ActivityEventType.COMMIT_CREATED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());

            ActivitySavedEvent event = new ActivitySavedEvent(
                Optional.of(testUser),
                ActivityEventType.COMMIT_CREATED,
                historicalTimestamp,
                1L,
                ActivityTargetType.COMMIT,
                200L
            );

            service.checkAndUnlock(event);

            verify(userAchievementRepository).save(
                argThat(ua -> ua.getUnlockedAt() != null && ua.getUnlockedAt().equals(historicalTimestamp))
            );
        }
    }

    @Nested
    @DisplayName("Dirty-Check Optimization")
    class DirtyCheckTests {

        @Test
        @DisplayName("saves when progress changes even without unlock")
        void savesWhenProgressChanges() {
            AchievementDefinition def = new AchievementDefinition(
                "pr.merged.rare",
                AchievementCategory.PULL_REQUESTS,
                AchievementRarity.RARE,
                new LinearAchievementProgress(0, 15),
                "pr.merged.uncommon",
                false,
                Set.of(ActivityEventType.PULL_REQUEST_MERGED),
                "StandardCountEvaluator"
            );

            UserAchievement existingProgress = UserAchievement.builder()
                .user(testUser)
                .achievementId("pr.merged.rare")
                .progressData(new LinearAchievementProgress(3, 15))
                .build();

            when(achievementRegistry.getByTriggerEvent(ActivityEventType.PULL_REQUEST_MERGED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(
                List.of(existingProgress)
            );

            service.checkAndUnlock(createEvent(ActivityEventType.PULL_REQUEST_MERGED));

            // StandardCountEvaluator always increments, so progress changed (3→4), save is called
            verify(userAchievementRepository).save(existingProgress);
            LinearAchievementProgress progress = (LinearAchievementProgress) existingProgress.getProgressData();
            assertThat(progress.current()).isEqualTo(4);
        }
    }
}

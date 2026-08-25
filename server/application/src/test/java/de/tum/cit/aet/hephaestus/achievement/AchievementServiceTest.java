package de.tum.cit.aet.hephaestus.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.achievement.evaluator.AchievementEvaluator;
import de.tum.cit.aet.hephaestus.achievement.evaluator.StandardCountEvaluator;
import de.tum.cit.aet.hephaestus.achievement.progress.LinearAchievementProgress;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.activity.ActivitySavedEvent;
import de.tum.cit.aet.hephaestus.activity.ActivityTargetType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.ObjectMapper;

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

        service = new AchievementService(
            userAchievementRepository,
            evaluators,
            achievementRegistry,
            cacheManager,
            new ObjectMapper()
        );
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
    class CheckAndUnlockTests {

        @Test
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
        @DisplayName("acquires the per-user advisory lock before reading progress")
        void acquiresUserLockBeforeReads() {
            // Lock must precede any read so it can't be reordered after a flush-triggering query.
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
            when(achievementRegistry.getByTriggerEvent(ActivityEventType.PULL_REQUEST_MERGED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());
            when(
                userAchievementRepository.insertIfAbsent(any(UUID.class), eq(1L), anyString(), anyString(), any())
            ).thenReturn(1);

            service.checkAndUnlock(createEvent(ActivityEventType.PULL_REQUEST_MERGED));

            InOrder inOrder = inOrder(userAchievementRepository);
            inOrder.verify(userAchievementRepository).acquireUserLock(1L);
            inOrder.verify(userAchievementRepository).findByUserIdAndAchievementIdIn(eq(1L), any());
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
            when(
                userAchievementRepository.insertIfAbsent(
                    any(UUID.class),
                    eq(1L),
                    eq("commit.common.1"),
                    anyString(),
                    any()
                )
            ).thenReturn(1);

            List<AchievementDefinition> result = service.checkAndUnlock(createEvent(ActivityEventType.COMMIT_CREATED));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo("commit.common.1");
            verify(userAchievementRepository).insertIfAbsent(
                any(UUID.class),
                eq(1L),
                eq("commit.common.1"),
                anyString(),
                any()
            );
            verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        }

        @Test
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
            when(
                userAchievementRepository.insertIfAbsent(
                    any(UUID.class),
                    eq(1L),
                    eq("commit.common.1"),
                    anyString(),
                    eq(historicalTimestamp)
                )
            ).thenReturn(1);

            ActivitySavedEvent event = new ActivitySavedEvent(
                Optional.of(testUser),
                ActivityEventType.COMMIT_CREATED,
                historicalTimestamp,
                1L,
                ActivityTargetType.COMMIT,
                200L
            );

            service.checkAndUnlock(event);

            verify(userAchievementRepository).insertIfAbsent(
                any(UUID.class),
                eq(1L),
                eq("commit.common.1"),
                anyString(),
                eq(historicalTimestamp)
            );
        }
    }

    @Nested
    class ConcurrentInsertTests {

        private AchievementDefinition newAchievement() {
            return new AchievementDefinition(
                "commit.common.1",
                AchievementCategory.COMMITS,
                AchievementRarity.COMMON,
                new LinearAchievementProgress(0, 5),
                null,
                false,
                Set.of(ActivityEventType.COMMIT_CREATED),
                "StandardCountEvaluator"
            );
        }

        @Test
        void shouldNotThrowWhenConcurrentInsertsCollideOnUniqueConstraint() {
            AchievementDefinition def = newAchievement();
            when(achievementRegistry.getByTriggerEvent(ActivityEventType.COMMIT_CREATED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());
            // Simulate the losing side of the race: another @Async listener already
            // inserted a row for (userId=1, achievementId=commit.common.1) between
            // our findByUserIdAndAchievementIdIn read and our upsert.
            when(
                userAchievementRepository.insertIfAbsent(
                    any(UUID.class),
                    eq(1L),
                    eq("commit.common.1"),
                    anyString(),
                    any()
                )
            ).thenReturn(0);

            UserAchievement persistedByWinner = UserAchievement.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .achievementId("commit.common.1")
                .progressData(new LinearAchievementProgress(1, 5))
                .build();
            when(userAchievementRepository.findByUserIdAndAchievementId(1L, "commit.common.1")).thenReturn(
                Optional.of(persistedByWinner)
            );

            // Must not throw DataIntegrityViolationException or anything else.
            List<AchievementDefinition> result = service.checkAndUnlock(createEvent(ActivityEventType.COMMIT_CREATED));

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReapplyEvaluatorOnWinningRowWhenInsertLosesRace() {
            AchievementDefinition def = newAchievement();
            when(achievementRegistry.getByTriggerEvent(ActivityEventType.COMMIT_CREATED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());
            when(
                userAchievementRepository.insertIfAbsent(
                    any(UUID.class),
                    eq(1L),
                    eq("commit.common.1"),
                    anyString(),
                    any()
                )
            ).thenReturn(0);

            // Winning transaction committed progress=1 (their event). Our event
            // must still bump it to 2 so the user is not under-counted.
            UserAchievement persistedByWinner = UserAchievement.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .achievementId("commit.common.1")
                .progressData(new LinearAchievementProgress(1, 5))
                .build();
            when(userAchievementRepository.findByUserIdAndAchievementId(1L, "commit.common.1")).thenReturn(
                Optional.of(persistedByWinner)
            );

            service.checkAndUnlock(createEvent(ActivityEventType.COMMIT_CREATED));

            verify(userAchievementRepository).save(persistedByWinner);
            LinearAchievementProgress finalProgress = (LinearAchievementProgress) persistedByWinner.getProgressData();
            assertThat(finalProgress.current()).isEqualTo(2);
        }

        @Test
        void shouldSkipReapplyWhenWinningRowIsAlreadyUnlocked() {
            AchievementDefinition def = newAchievement();
            when(achievementRegistry.getByTriggerEvent(ActivityEventType.COMMIT_CREATED)).thenReturn(List.of(def));
            when(userAchievementRepository.findByUserIdAndAchievementIdIn(eq(1L), any())).thenReturn(List.of());
            when(
                userAchievementRepository.insertIfAbsent(
                    any(UUID.class),
                    eq(1L),
                    eq("commit.common.1"),
                    anyString(),
                    any()
                )
            ).thenReturn(0);

            UserAchievement alreadyUnlocked = UserAchievement.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .achievementId("commit.common.1")
                .progressData(new LinearAchievementProgress(5, 5))
                .unlockedAt(Instant.parse("2024-08-14T10:00:00Z"))
                .build();
            when(userAchievementRepository.findByUserIdAndAchievementId(1L, "commit.common.1")).thenReturn(
                Optional.of(alreadyUnlocked)
            );

            service.checkAndUnlock(createEvent(ActivityEventType.COMMIT_CREATED));

            verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        }
    }

    @Nested
    class DirtyCheckTests {

        @Test
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

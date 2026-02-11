package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for evaluating and unlocking achievements via incremental progress updates.
 *
 * <p>This service is the core of the achievement system. It:
 * <ul>
 *   <li>Increments progress counters on {@link UserAchievement} entities when qualifying
 *       activity events occur</li>
 *   <li>Compares current progress against achievement thresholds</li>
 *   <li>Unlocks achievements by setting {@code unlockedAt} when the threshold is met</li>
 * </ul>
 *
 * <h3>Achievement Evaluation Flow (Incremental)</h3>
 * <pre>
 * 1. Receive event type that was just recorded
 * 2. Find all achievements triggered by that event type
 * 3. Fetch (or create) UserAchievement progress records
 * 4. Increment currentValue via {@link AchievementEvaluator}
 * 5. If currentValue >= requiredCount AND not yet unlocked, set unlockedAt
 * 6. Save the progress record
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Achievement progress updates are idempotent in structure due to the unique
 * constraint on (user_id, achievement_id). Concurrent increment attempts are
 * serialized by the {@code @Transactional} boundary.
 *
 * @see AchievementEventListener
 * @see AchievementType
 * @see AchievementEvaluator
 */
@Service
public class AchievementService {

    /**
     * Cache name for user achievement progress.
     * <p>Key format: user login (workspace-scoped).
     */
    public static final String ACHIEVEMENT_PROGRESS_CACHE = "achievementProgress";

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private final UserAchievementRepository userAchievementRepository;
    private final AchievementEvaluator achievementEvaluator;

    public AchievementService(UserAchievementRepository userAchievementRepository) {
        this.userAchievementRepository = userAchievementRepository;
        this.achievementEvaluator = new StandardCountEvaluator();
    }

    /**
     * Check and unlock any achievements triggered by the given event type.
     *
     * <p>This method increments progress on all achievements triggered by the
     * specified event type and unlocks any that have reached their threshold.
     *
     * @param user the user to check achievements for
     * @param eventType the activity event type that was just recorded
     * @return list of newly unlocked achievement types (empty if none)
     */
    @CacheEvict(value = ACHIEVEMENT_PROGRESS_CACHE, key = "#user.login", condition = "#user != null")
    @Transactional
    public List<AchievementType> checkAndUnlock(User user, ActivityEventType eventType) {
        if (user == null) {
            log.debug("Skipping achievement check: user is null");
            return List.of();
        }

        // Find achievements triggered by this event type
        List<AchievementType> candidates = AchievementType.getByTriggerEvent(eventType);
        if (candidates.isEmpty()) {
            log.debug("No achievements triggered by event type: {}", eventType);
            return List.of();
        }

        // Fetch existing progress records for the triggered achievement types
        Set<String> candidateIds = candidates.stream().map(AchievementType::getId).collect(Collectors.toSet());

        Map<String, UserAchievement> existingMap = userAchievementRepository
            .findByUserIdAndAchievementIdIn(user.getId(), candidateIds)
            .stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity()));

        List<AchievementType> newlyUnlocked = new ArrayList<>();

        for (AchievementType achievement : candidates) {
            // Get existing progress or create a new record
            UserAchievement progress = existingMap.get(achievement.getId());
            if (progress == null) {
                progress = UserAchievement.builder()
                    .user(user)
                    .achievementId(achievement.getId())
                    .currentValue(0)
                    .build();
            }

            // Skip if already unlocked
            if (progress.getUnlockedAt() != null) {
                continue;
            }

            // Increment progress
            achievementEvaluator.updateProgress(progress, eventType);

            // Check if threshold is now met
            if (progress.getCurrentValue() >= achievement.getRequiredCount()) {
                progress.setUnlockedAt(Instant.now());
                newlyUnlocked.add(achievement);
                log.info(
                    "Achievement unlocked: userId={}, achievement={}, count={}, required={}",
                    user.getId(),
                    achievement.getId(),
                    progress.getCurrentValue(),
                    achievement.getRequiredCount()
                );
            }

            userAchievementRepository.save(progress);
        }

        return newlyUnlocked;
    }

    /**
     * Get all unlocked achievements for a user.
     *
     * @param userId the user's ID
     * @return list of unlocked achievements with metadata
     */
    @Transactional(readOnly = true)
    public List<UserAchievement> getUnlockedAchievements(Long userId) {
        return userAchievementRepository
            .findByUserId(userId)
            .stream()
            .filter(ua -> ua.getUnlockedAt() != null)
            .toList();
    }

    /**
     * Check if a specific achievement is unlocked for a user.
     *
     * @param userId the user's ID
     * @param achievementType the achievement to check
     * @return true if the achievement is unlocked
     */
    @Transactional(readOnly = true)
    public boolean isUnlocked(Long userId, AchievementType achievementType) {
        return userAchievementRepository
            .findByUserIdAndAchievementId(userId, achievementType.getId())
            .map(ua -> ua.getUnlockedAt() != null)
            .orElse(false);
    }

    /**
     * Get all achievements with progress information for a user.
     *
     * <p>This method reads progress directly from {@link UserAchievement} records
     * without performing any count queries on the activity event table.
     *
     * <h3>Status Logic</h3>
     * <ul>
     *   <li>{@code UNLOCKED} - {@code unlockedAt} is non-null on UserAchievement</li>
     *   <li>{@code AVAILABLE} - Not unlocked, but parent is unlocked (or no parent)</li>
     *   <li>{@code LOCKED} - Parent achievement is not yet unlocked</li>
     * </ul>
     *
     * @param user the user to get achievements for
     * @return list of all achievement DTOs with progress, ordered by category and level
     */
    @Cacheable(value = ACHIEVEMENT_PROGRESS_CACHE, key = "#user.login")
    @Transactional(readOnly = true)
    public List<AchievementDTO> getAllAchievementsWithProgress(User user) {
        if (user == null) {
            log.debug("Returning empty achievements: user is null");
            return List.of();
        }

        Long userId = user.getId();

        // Fetch all progress records for this user (both in-progress and unlocked)
        Map<String, UserAchievement> progressMap = userAchievementRepository
            .findByUserId(userId)
            .stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity()));

        // Build DTOs for all achievements
        List<AchievementDTO> result = new ArrayList<>();
        for (AchievementType type : AchievementType.values()) {
            UserAchievement progress = progressMap.get(type.getId());
            long currentProgress = progress != null ? progress.getCurrentValue() : 0;
            Instant unlockedAt = progress != null ? progress.getUnlockedAt() : null;
            AchievementStatus status = computeStatus(type, progressMap);

            result.add(AchievementDTO.fromType(type, currentProgress, status, unlockedAt));
        }

        return result;
    }

    /**
     * Compute the status of an achievement based on unlock state and parent chain.
     *
     * <ul>
     *   <li>UNLOCKED if {@code unlockedAt} is non-null in progressMap</li>
     *   <li>AVAILABLE if not unlocked and (no parent OR parent is unlocked)</li>
     *   <li>LOCKED if not unlocked and parent is not unlocked</li>
     * </ul>
     */
    private AchievementStatus computeStatus(AchievementType type, Map<String, UserAchievement> progressMap) {
        // Check if this achievement is unlocked
        UserAchievement progress = progressMap.get(type.getId());
        if (progress != null && progress.getUnlockedAt() != null) {
            return AchievementStatus.UNLOCKED;
        }

        // Check parent
        AchievementType parent = type.getParent();
        if (parent == null) {
            // Root achievement with no parent is always available
            return AchievementStatus.AVAILABLE;
        }

        // Parent must be unlocked for this to be available
        UserAchievement parentProgress = progressMap.get(parent.getId());
        if (parentProgress != null && parentProgress.getUnlockedAt() != null) {
            return AchievementStatus.AVAILABLE;
        }

        return AchievementStatus.LOCKED;
    }
}

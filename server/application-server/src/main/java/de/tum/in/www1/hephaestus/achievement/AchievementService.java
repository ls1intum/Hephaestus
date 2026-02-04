package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for evaluating and unlocking achievements.
 *
 * <p>This service is the core of the achievement system. It:
 * <ul>
 *   <li>Queries activity event counts from the ledger</li>
 *   <li>Compares counts against achievement thresholds</li>
 *   <li>Unlocks achievements that haven't been unlocked yet</li>
 * </ul>
 *
 * <h3>Achievement Evaluation Flow</h3>
 * <pre>
 * 1. Receive event type that was just recorded
 * 2. Find all achievements triggered by that event type
 * 3. For each achievement:
 *    a. Skip if already unlocked
 *    b. Count relevant events for user
 *    c. If count >= threshold, unlock
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Achievement unlocks are idempotent due to the unique constraint on
 * (user_id, achievement_id). Concurrent unlock attempts will result in
 * one success and others being silently ignored.
 *
 * @see AchievementEventListener
 * @see AchievementType
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private final UserAchievementRepository userAchievementRepository;
    private final ActivityEventRepository activityEventRepository;

    public AchievementService(
        UserAchievementRepository userAchievementRepository,
        ActivityEventRepository activityEventRepository
    ) {
        this.userAchievementRepository = userAchievementRepository;
        this.activityEventRepository = activityEventRepository;
    }

    /**
     * Check and unlock any achievements triggered by the given event type.
     *
     * <p>This method evaluates all achievements that are triggered by the
     * specified event type and unlocks any that the user has earned.
     *
     * @param user the user to check achievements for
     * @param eventType the activity event type that was just recorded
     * @return list of newly unlocked achievement types (empty if none)
     */
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

        // Get user's existing achievements to avoid re-checking
        Set<String> existingAchievements = userAchievementRepository.findAchievementIdsByUserId(user.getId());

        List<AchievementType> newlyUnlocked = new ArrayList<>();

        for (AchievementType achievement : candidates) {
            // Skip if already unlocked
            if (existingAchievements.contains(achievement.getId())) {
                continue;
            }

            // Count events for this achievement's trigger events
            long count = countEventsForUser(user.getId(), achievement.getTriggerEvents());

            // Check if threshold met
            if (count >= achievement.getRequiredCount()) {
                unlock(user, achievement);
                newlyUnlocked.add(achievement);
                log.info(
                    "Achievement unlocked: userId={}, achievement={}, count={}, required={}",
                    user.getId(),
                    achievement.getId(),
                    count,
                    achievement.getRequiredCount()
                );
            }
        }

        return newlyUnlocked;
    }

    /**
     * Count activity events of specified types for a user.
     *
     * @param userId the user's ID
     * @param eventTypes the event types to count
     * @return total count of matching events
     */
    private long countEventsForUser(Long userId, Set<ActivityEventType> eventTypes) {
        if (eventTypes.isEmpty()) {
            return 0;
        }

        // Convert enum set to string set for the query
        Set<String> eventTypeNames = eventTypes.stream()
            .map(ActivityEventType::name)
            .collect(java.util.stream.Collectors.toSet());

        return activityEventRepository.countByActorIdAndEventTypes(userId, eventTypeNames);
    }

    /**
     * Unlock an achievement for a user.
     *
     * @param user the user
     * @param achievement the achievement to unlock
     */
    private void unlock(User user, AchievementType achievement) {
        UserAchievement unlock = UserAchievement.builder()
            .user(user)
            .achievementId(achievement.getId())
            .unlockedAt(Instant.now())
            .build();

        userAchievementRepository.save(unlock);
    }

    /**
     * Get all unlocked achievements for a user.
     *
     * @param userId the user's ID
     * @return list of unlocked achievements with metadata
     */
    @Transactional(readOnly = true)
    public List<UserAchievement> getUnlockedAchievements(Long userId) {
        return userAchievementRepository.findByUserId(userId);
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
        return userAchievementRepository.existsByUserIdAndAchievementId(
            userId,
            achievementType.getId()
        );
    }

    /**
     * Get all achievements with progress information for a user.
     *
     * <p>This method efficiently aggregates achievement data by:
     * <ol>
     *   <li>Fetching all user's unlocked achievements in a single query</li>
     *   <li>Identifying unique trigger event sets across all achievements</li>
     *   <li>Performing one count query per unique trigger set (not per achievement)</li>
     *   <li>Computing status based on unlock state and parent chain</li>
     * </ol>
     *
     * <h3>Status Logic</h3>
     * <ul>
     *   <li>{@code UNLOCKED} - Achievement exists in UserAchievement table</li>
     *   <li>{@code AVAILABLE} - Not unlocked, but parent is unlocked (or no parent)</li>
     *   <li>{@code LOCKED} - Parent achievement is not yet unlocked</li>
     * </ul>
     *
     * @param user the user to get achievements for
     * @return list of all achievement DTOs with progress, ordered by category and level
     */
    @Transactional(readOnly = true)
    public List<AchievementDTO> getAllAchievementsWithProgress(User user) {
        if (user == null) {
            log.debug("Returning empty achievements: user is null");
            return List.of();
        }

        Long userId = user.getId();

        // Step 1: Fetch all unlocked achievements for this user (single query)
        Map<String, UserAchievement> unlockedMap = userAchievementRepository
            .findByUserId(userId)
            .stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity()));

        // Step 2: Identify unique trigger event sets and batch count
        Map<Set<ActivityEventType>, Long> countsByTriggerSet = batchCountByTriggerSets(userId);

        // Step 3: Build DTOs for all achievements
        List<AchievementDTO> result = new ArrayList<>();
        for (AchievementType type : AchievementType.values()) {
            UserAchievement unlocked = unlockedMap.get(type.getId());
            long progress = getProgressFromCounts(type, countsByTriggerSet);
            AchievementStatus status = computeStatus(type, unlockedMap);
            Instant unlockedAt = unlocked != null ? unlocked.getUnlockedAt() : null;

            result.add(AchievementDTO.fromType(type, progress, status, unlockedAt));
        }

        return result;
    }

    /**
     * Batch count events by unique trigger event sets.
     *
     * <p>Many achievements share the same trigger events (e.g., all PR achievements
     * use PULL_REQUEST_MERGED). This method identifies unique sets and performs
     * one count query per unique set, avoiding redundant database calls.
     *
     * @param userId the user's ID
     * @return map from trigger event set to count
     */
    private Map<Set<ActivityEventType>, Long> batchCountByTriggerSets(Long userId) {
        // Collect unique trigger sets
        Set<Set<ActivityEventType>> uniqueTriggerSets = java.util.Arrays.stream(AchievementType.values())
            .map(AchievementType::getTriggerEvents)
            .filter(set -> !set.isEmpty())
            .collect(Collectors.toSet());

        Map<Set<ActivityEventType>, Long> counts = new HashMap<>();

        for (Set<ActivityEventType> triggerSet : uniqueTriggerSets) {
            long count = countEventsForUser(userId, triggerSet);
            counts.put(triggerSet, count);
        }

        return counts;
    }

    /**
     * Get progress count for an achievement from the pre-computed counts map.
     */
    private long getProgressFromCounts(AchievementType type, Map<Set<ActivityEventType>, Long> countsByTriggerSet) {
        Set<ActivityEventType> triggers = type.getTriggerEvents();
        if (triggers.isEmpty()) {
            return 0;
        }
        return countsByTriggerSet.getOrDefault(triggers, 0L);
    }

    /**
     * Compute the status of an achievement based on unlock state and parent chain.
     *
     * <ul>
     *   <li>UNLOCKED if present in unlockedMap</li>
     *   <li>AVAILABLE if not unlocked and (no parent OR parent is unlocked)</li>
     *   <li>LOCKED if not unlocked and parent is not unlocked</li>
     * </ul>
     */
    private AchievementStatus computeStatus(AchievementType type, Map<String, UserAchievement> unlockedMap) {
        // Already unlocked
        if (unlockedMap.containsKey(type.getId())) {
            return AchievementStatus.UNLOCKED;
        }

        // Check parent
        AchievementType parent = type.getParent();
        if (parent == null) {
            // Root achievement with no parent is always available
            return AchievementStatus.AVAILABLE;
        }

        // Parent must be unlocked for this to be available
        if (unlockedMap.containsKey(parent.getId())) {
            return AchievementStatus.AVAILABLE;
        }

        return AchievementStatus.LOCKED;
    }
}

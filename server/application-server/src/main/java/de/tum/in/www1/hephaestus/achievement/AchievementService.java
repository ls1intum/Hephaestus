package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.achievement.evaluator.AchievementEvaluator;
import de.tum.in.www1.hephaestus.achievement.progress.AchievementProgress;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
 * <h3>Strategy Pattern</h3>
 * <p>Each {@link AchievementDefinition} declares its evaluator class via
 * {@link AchievementDefinition#evaluatorClass()}. This service builds a strategy map from
 * all Spring-managed {@link AchievementEvaluator} beans at startup, keyed by their
 * concrete class. At evaluation time, the correct evaluator is resolved from this map
 * without any switch/if logic — ensuring the system is open for extension (add a new
 * {@code @Component} implementing {@link AchievementEvaluator}) without modifying this service.
 *
 * <h3>Achievement Evaluation Flow (Incremental)</h3>
 * <pre>
 * 1. Receive activity saved event that was just recorded
 * 2. Find all achievements triggered by that event type
 * 3. Fetch (or create) UserAchievement progress records
 * 4. Resolve the evaluator for each achievement via type.getEvaluatorClass()
 * 5. Increment currentValue via the resolved {@link AchievementEvaluator} (passing the event context)
 * 6. If currentValue &gt;= requiredCount AND not yet unlocked, set unlockedAt
 * 7. Save the progress record
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Achievement progress updates are idempotent in structure due to the unique
 * constraint on (user_id, achievement_id). Concurrent increment attempts are
 * serialized by the {@code @Transactional} boundary.
 *
 * @see AchievementEventListener
 * @see AchievementDefinition
 * @see AchievementEvaluator
 */
@Slf4j
@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("Achievements are per-user lifetime accomplishments, not workspace-scoped")
public class AchievementService {

    /**
     * Cache name for user achievement progress.
     * <p>Key format: user login (workspace-scoped).
     */
    public static final String ACHIEVEMENT_PROGRESS_CACHE = "achievementProgress";
    private final UserAchievementRepository userAchievementRepository;

    /**
     * All evaluator beans injected by Spring; used to build the strategy map at startup.
     */
    private final List<AchievementEvaluator> evaluators;

    private final AchievementRegistry achievementRegistry;
    private final CacheManager cacheManager;

    /**
     * Strategy map: maps each evaluator's concrete class to its Spring-managed bean instance.
     * Built once at startup from all {@link AchievementEvaluator} beans in the context.
     */
    private volatile Map<Class<? extends AchievementEvaluator>, AchievementEvaluator> evaluatorMap;

    @PostConstruct
    void initEvaluatorMap() {
        this.evaluatorMap = evaluators
            .stream()
            .collect(Collectors.toMap(AchievementEvaluator::getClass, Function.identity()));
        log.info(
            "Registered {} achievement evaluator(s): {}",
            evaluatorMap.size(),
            evaluatorMap.keySet().stream().map(Class::getSimpleName).toList()
        );
    }

    /**
     * Resolve the evaluator bean for the given achievement definition.
     *
     * @param definition the achievement definition whose evaluator to resolve
     * @return the Spring-managed evaluator instance
     * @throws IllegalStateException if no evaluator bean is registered for the definition's evaluatorClass
     */
    private AchievementEvaluator resolveEvaluator(AchievementDefinition definition) {
        String className = definition.evaluatorClass();
        if (!className.contains(".")) {
            className = "de.tum.in.www1.hephaestus.achievement.evaluator." + className;
        }

        try {
            Class<?> clazz = Class.forName(className);
            if (!AchievementEvaluator.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException(
                    "Evaluator class does not implement AchievementEvaluator: " + definition.evaluatorClass()
                );
            }
            @SuppressWarnings("unchecked")
            AchievementEvaluator evaluator = evaluatorMap.get((Class<? extends AchievementEvaluator>) clazz);
            if (evaluator == null) {
                throw new IllegalStateException(
                    "No AchievementEvaluator bean registered for class: " +
                        definition.evaluatorClass() +
                        " (required by achievement " +
                        definition.id() +
                        ")"
                );
            }
            return evaluator;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Evaluator class not found: " + definition.evaluatorClass(), e);
        }
    }

    /**
     * Check and unlock any achievements triggered by the given activity saved event.
     *
     * <p>This method increments progress on all achievements triggered by the
     * event's type and unlocks any that have reached their threshold.
     *
     * <p>The {@code occurredAt} timestamp from the event is used as the unlock time instead of
     * {@link Instant#now()}. This ensures that achievements unlocked during
     * historical data syncs or backfills carry the timestamp of the earliest
     * qualifying activity event, not the moment of processing.
     *
     * @param event the activity saved event containing user, type, and timestamp
     * @return list of newly unlocked achievement types (empty if none)
     */
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public List<AchievementDefinition> checkAndUnlock(ActivitySavedEvent event) {
        if (event.user().isEmpty()) {
            log.debug("Skipping achievement check: user is empty");
            return List.of();
        }

        User user = event.user().get();
        ActivityEventType eventType = event.eventType();
        Instant occurredAt = event.occurredAt();

        // Find achievements triggered by this event type
        List<AchievementDefinition> candidates = achievementRegistry.getByTriggerEvent(eventType);
        if (candidates.isEmpty()) {
            log.debug("No achievements triggered by event type: {}", eventType);
            return List.of();
        }

        // Fetch existing progress records for the triggered achievement types
        Set<String> candidateIds = candidates.stream().map(AchievementDefinition::id).collect(Collectors.toSet());

        Map<String, UserAchievement> existingMap = userAchievementRepository
            .findByUserIdAndAchievementIdIn(user.getId(), candidateIds)
            .stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity()));

        List<AchievementDefinition> newlyUnlocked = new ArrayList<>();

        for (AchievementDefinition achievementDefinition : candidates) {
            // Get existing progress or create a new record
            UserAchievement uaProgress = existingMap.get(achievementDefinition.id());
            boolean isNew = uaProgress == null;
            if (isNew) {
                uaProgress = UserAchievement.builder()
                    .user(user)
                    .achievementId(achievementDefinition.id())
                    .progressData(achievementDefinition.requirements())
                    .build();
            }

            // Skip if already unlocked
            if (uaProgress.getUnlockedAt() != null) {
                continue;
            }

            // Snapshot progress before evaluation for dirty-checking
            AchievementProgress progressBefore = uaProgress.getProgressData();

            // Resolve evaluator and increment progress
            AchievementEvaluator evaluator = resolveEvaluator(achievementDefinition);
            boolean wasUnlocked = evaluator.updateProgress(uaProgress, event);

            if (wasUnlocked) {
                uaProgress.setUnlockedAt(occurredAt);
                newlyUnlocked.add(achievementDefinition);
                log.info(
                    "Achievement unlocked: userId={}, achievement={}, progress={}, occurredAt={}",
                    user.getId(),
                    achievementDefinition.id(),
                    uaProgress.getProgressData(),
                    occurredAt
                );
            }

            // Only persist if entity is new or progress actually changed
            boolean progressChanged = !uaProgress.getProgressData().equals(progressBefore);
            if (isNew || progressChanged || wasUnlocked) {
                userAchievementRepository.save(uaProgress);
                evictAchievementCache(user.getId());
            }
        }

        return newlyUnlocked;
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
    @Cacheable(value = ACHIEVEMENT_PROGRESS_CACHE, key = "#user.id")
    @Transactional(readOnly = true)
    public List<AchievementDTO> getAllAchievementsWithProgress(User user) {
        Long userId = user.getId();

        // Fetch all progress records for this user (both in-progress and unlocked)
        Map<String, UserAchievement> progressMap = userAchievementRepository
            .findByUserId(userId)
            .stream()
            .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity()));

        // Build DTOs for all achievements
        List<AchievementDTO> result = new ArrayList<>();
        for (AchievementDefinition achievement : achievementRegistry.values()) {
            UserAchievement progress = progressMap.get(achievement.id());

            AchievementStatus status = computeStatus(achievement, progressMap);
            if (status == AchievementStatus.HIDDEN) {
                continue;
            }

            AchievementProgress progressData =
                progress != null ? progress.getProgressData() : achievement.requirements();
            Optional<Instant> unlockedAt =
                progress != null ? Optional.ofNullable(progress.getUnlockedAt()) : Optional.empty();
            result.add(AchievementDTO.fromDefinition(achievement, status, progressData, unlockedAt));
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
    private AchievementStatus computeStatus(
        AchievementDefinition achievement,
        Map<String, UserAchievement> progressMap
    ) {
        // Check if this achievement is unlocked
        UserAchievement progress = progressMap.get(achievement.id());
        if (progress != null && progress.getUnlockedAt() != null) {
            return AchievementStatus.UNLOCKED;
        }

        // If not unlocked, check if it's hidden
        if (achievement.isHidden()) {
            return AchievementStatus.HIDDEN;
        }

        // Check parent
        String parentId = achievement.parent();
        if (parentId == null || parentId.isEmpty()) {
            // Root achievement with no parent is always available
            return AchievementStatus.AVAILABLE;
        }

        // Parent must be unlocked for this to be available
        UserAchievement parentProgress = progressMap.get(parentId);
        if (parentProgress != null && parentProgress.getUnlockedAt() != null) {
            return AchievementStatus.AVAILABLE;
        }

        return AchievementStatus.LOCKED;
    }

    /**
     * Evict the achievement progress cache for a specific user.
     * Called only when progress actually changes, avoiding unnecessary cache invalidation.
     */
    void evictAchievementCache(Long userId) {
        Cache cache = cacheManager.getCache(ACHIEVEMENT_PROGRESS_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getAllAchievementDefinitionIds() {
        return achievementRegistry.values().stream().map(AchievementDefinition::id).toList();
    }

    /**
     * Return all definitions as LOCKED with 0 progress
     */
    @Transactional(readOnly = true)
    public List<AchievementDTO> getAllAchievementDefinitions() {
        return achievementRegistry
            .values()
            .stream()
            .map(def ->
                AchievementDTO.fromDefinition(def, AchievementStatus.LOCKED, def.requirements(), Optional.empty())
            )
            .toList();
    }
}

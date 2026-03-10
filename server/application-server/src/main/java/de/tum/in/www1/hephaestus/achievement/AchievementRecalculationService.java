package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service dedicated strictly to recalculating achievement progress from scratch.
 *
 * <p>This resolves the issue where historical workspace data syncs would grant
 * achievements with "today's" timestamp, and out-of-order syncing could cause
 * the wrong event to mathematically trigger an unlock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementRecalculationService {

    private static final ConcurrentHashMap<Long, Boolean> ACTIVE_RECALCULATIONS = new ConcurrentHashMap<>();

    private final UserAchievementRepository userAchievementRepository;
    private final ActivityEventRepository activityEventRepository;
    private final AchievementService achievementService;

    /**
     * Fully recalculate all achievement unlocks for a given user from their activity history.
     *
     * <p>This method:
     * <p>1. Wipes the user's existing progress.
     * <p>2. Streams their complete timeline of activity events chronologically.
     * <p>3. Feeds them back into the incremental check-and-unlock logic.
     *
     * @param user the user to recalculate achievements for
     */
    @Async
    @CacheEvict(value = AchievementService.ACHIEVEMENT_PROGRESS_CACHE, key = "#user.id", condition = "#user != null")
    public void recalculateUser(User user) {
        if (ACTIVE_RECALCULATIONS.putIfAbsent(user.getId(), Boolean.TRUE) != null) {
            log.warn(
                "Recalculation already in progress for user: userId={}, login={}. Skipping.",
                user.getId(),
                LoggingUtils.sanitizeForLog(user.getLogin())
            );
            return;
        }

        try {
            recalculateUserInternal(user);
        } finally {
            ACTIVE_RECALCULATIONS.remove(user.getId());
        }
    }

    @Transactional
    private void recalculateUserInternal(User user) {
        log.info(
            "Starting complete achievement recalculation for user: userId={}, login={}",
            user.getId(),
            LoggingUtils.sanitizeForLog(user.getLogin())
        );

        // 1. Wipe existing state
        userAchievementRepository.deleteByUserId(user.getId());

        // 2. Replay all events chronologically in batches to avoid open-cursor DB errors
        int count = 0;
        Pageable pageable = PageRequest.of(0, 500);

        while (true) {
            Slice<ActivityEvent> slice = activityEventRepository.findSliceByActorIdOrderByOccurredAtAsc(
                user.getId(),
                pageable
            );
            for (ActivityEvent event : slice.getContent()) {
                var workspace = event.getWorkspace();
                if (workspace == null) {
                    log.warn("Skipping activity event with null workspace: eventId={}", event.getId());
                    continue;
                }
                ActivitySavedEvent savedEvent = new ActivitySavedEvent(
                    Optional.ofNullable(event.getActor()),
                    event.getEventType(),
                    event.getOccurredAt(),
                    workspace.getId(),
                    ActivityTargetType.fromValue(event.getTargetType()),
                    event.getTargetId()
                );
                try {
                    achievementService.checkAndUnlock(savedEvent);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn(
                        "Optimistic locking conflict during recalculation for user {}, eventId={}: {}",
                        LoggingUtils.sanitizeForLog(user.getLogin()),
                        event.getId(),
                        e.getMessage()
                    );
                }
                count++;

                if (count % 1000 == 0) {
                    log.debug(
                        "Recalculation progress for user {}: processed {} events",
                        LoggingUtils.sanitizeForLog(user.getLogin()),
                        count
                    );
                }
            }
            if (!slice.hasNext()) {
                break;
            }
            pageable = slice.nextPageable();
        }

        log.info(
            "Completed achievement recalculation for user: login={}, eventsProcessed={}",
            LoggingUtils.sanitizeForLog(user.getLogin()),
            count
        );
    }
}

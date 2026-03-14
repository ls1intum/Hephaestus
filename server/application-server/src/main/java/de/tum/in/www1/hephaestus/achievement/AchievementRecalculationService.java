package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivitySavedEvent;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service dedicated strictly to recalculating achievement progress from scratch.
 *
 * <p>This resolves the issue where historical workspace data syncs would grant
 * achievements with "today's" timestamp, and out-of-order syncing could cause
 * the wrong event to mathematically trigger an unlock.
 *
 * <h3>Transaction Strategy</h3>
 * <p>The delete runs in its own transaction via {@link TransactionTemplate}, and each
 * event replay runs in the transaction provided by {@link AchievementService#checkAndUnlock}.
 * This avoids a single massive transaction for users with thousands of events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementRecalculationService {

    private static final ConcurrentHashMap<Long, Boolean> ACTIVE_RECALCULATIONS = new ConcurrentHashMap<>();

    private final UserAchievementRepository userAchievementRepository;
    private final ActivityEventRepository activityEventRepository;
    private final AchievementService achievementService;
    private final TransactionTemplate transactionTemplate;

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

    /**
     * Internal recalculation logic. Not {@code @Transactional} by design:
     * the delete runs in its own transaction, and each {@code checkAndUnlock}
     * call runs in the transaction boundary defined on {@link AchievementService}.
     */
    void recalculateUserInternal(User user) {
        log.info(
            "Starting complete achievement recalculation for user: userId={}, login={}",
            user.getId(),
            LoggingUtils.sanitizeForLog(user.getLogin())
        );

        // 1. Wipe existing state in its own transaction
        transactionTemplate.executeWithoutResult(status -> userAchievementRepository.deleteByUserId(user.getId()));

        // 2. Replay all events chronologically in batches to avoid open-cursor DB errors
        //    Each checkAndUnlock call runs in its own @Transactional boundary
        int count = 0;
        Pageable pageable = PageRequest.of(0, 500);

        while (true) {
            final Pageable currentPage = pageable;
            Slice<ActivityEvent> slice = transactionTemplate.execute(status ->
                activityEventRepository.findSliceByActorIdOrderByOccurredAtAsc(user.getId(), currentPage)
            );
            if (slice == null || !slice.hasContent()) {
                break;
            }
            for (ActivityEvent event : slice.getContent()) {
                ActivitySavedEvent savedEvent = new ActivitySavedEvent(
                    Optional.ofNullable(event.getActor()),
                    event.getEventType(),
                    event.getOccurredAt(),
                    event.getWorkspace().getId(),
                    ActivityTargetType.fromValue(event.getTargetType()),
                    event.getTargetId()
                );
                try {
                    achievementService.checkAndUnlock(savedEvent);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error(
                        "Optimistic locking conflict during recalculation (after retries exhausted) " +
                            "for user {}, eventId={}: {}. This event's progress increment was lost.",
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

        // Evict cache after all events have been replayed, not before
        achievementService.evictAchievementCache(user.getId());

        log.info(
            "Completed achievement recalculation for user: login={}, eventsProcessed={}",
            LoggingUtils.sanitizeForLog(user.getLogin()),
            count
        );
    }
}

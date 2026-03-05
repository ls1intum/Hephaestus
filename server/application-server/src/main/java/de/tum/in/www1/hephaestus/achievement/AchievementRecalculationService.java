package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

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
    @Transactional
    @CacheEvict(value = AchievementService.ACHIEVEMENT_PROGRESS_CACHE, key = "#user.id", condition = "#user != null")
    public void recalculateUser(User user) {
        log.info("Starting complete achievement recalculation for user: userId={}, login={}", user.getId(), user.getLogin());

        // 1. Wipe existing state
        userAchievementRepository.deleteByUserId(user.getId());

        // 2. Replay all events chronologically
        int count = 0;
        try (Stream<ActivityEvent> events = activityEventRepository.streamByActorIdOrderByOccurredAtAsc(user.getId())) {
            for (ActivityEvent event : (Iterable<ActivityEvent>) events::iterator) {
                achievementService.checkAndUnlock(user, event.getEventType(), event.getOccurredAt());
                count++;

                if (count % 1000 == 0) {
                    log.debug("Recalculation progress for user {}: processed {} events", user.getLogin(), count);
                }
            }
        }

        log.info("Completed achievement recalculation for user: login={}, eventsProcessed={}", user.getLogin(), count);
    }
}

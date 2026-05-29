package de.tum.cit.aet.hephaestus.achievement;

import de.tum.cit.aet.hephaestus.activity.ActivitySavedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that triggers achievement evaluation on activity events.
 *
 * <p>This listener bridges the activity module and the achievement module.
 * When an activity event is successfully recorded, this listener evaluates
 * whether the user has unlocked any new achievements.
 *
 * <h3>Event Processing</h3>
 * <ul>
 *   <li>Uses {@code @Async} to avoid blocking the main transaction</li>
 *   <li>Uses {@code AFTER_COMMIT} phase to ensure activity is persisted first</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>Achievement evaluation failures are logged but do not fail the original
 * activity recording. Achievements are a secondary feature that should not
 * block core functionality.
 *
 * @see ActivitySavedEvent
 * @see AchievementService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementEventListener {

    private final AchievementService achievementService;

    /**
     * Handle activity saved events to check for achievement unlocks.
     *
     * <p>This method is invoked asynchronously after the activity event
     * has been successfully committed to the database.
     *
     * @param event the activity saved event
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivitySaved(ActivitySavedEvent event) {
        if (event.user().isEmpty()) {
            log.debug(
                "Skipping achievement check for system event: eventType={} | Reason: event has no user",
                event.eventType()
            );
            return;
        }

        var user = event.user().get();
        // Eagerly extract ID (safe on detached proxies) for logging.
        // getLogin() may throw LazyInitializationException if the proxy is detached
        // (which happens because this listener is @Async + AFTER_COMMIT).
        Long userId = user.getId();

        try {
            List<AchievementDefinition> unlocked = achievementService.checkAndUnlock(event);

            if (!unlocked.isEmpty()) {
                log.info(
                    "User {} unlocked {} achievement(s): {}",
                    userId,
                    unlocked.size(),
                    unlocked.stream().map(AchievementDefinition::id).toList()
                );
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            // Residual contention past the advisory lock + retries; this increment is lost but
            // self-heals on the next event, so WARN without a stack trace (not an alert-worthy ERROR).
            log.warn(
                "Achievement increment lost to lock contention: userId={}, eventType={}",
                userId,
                event.eventType()
            );
        } catch (Exception e) {
            // Unexpected — keep ERROR; achievements are non-critical so we don't rethrow.
            log.error("Failed to evaluate achievements: userId={}, eventType={}", userId, event.eventType(), e);
        }
    }
}

package de.tum.in.www1.hephaestus.achievement;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 *   <li>Uses {@code REQUIRES_NEW} to run in a separate transaction</li>
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
@Component
public class AchievementEventListener {

    private static final Logger log = LoggerFactory.getLogger(AchievementEventListener.class);

    private final AchievementService achievementService;

    public AchievementEventListener(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    /**
     * Handle activity saved events to check for achievement unlocks.
     *
     * <p>This method is invoked asynchronously after the activity event
     * has been successfully committed to the database.
     *
     * @param event the activity saved event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivitySaved(ActivitySavedEvent event) {
        if (!event.hasUser()) {
            log.debug("Skipping achievement check for system event: eventType={}", event.eventType());
            return;
        }

        try {
            List<AchievementDefinition> unlocked = achievementService.checkAndUnlock(event.user(), event.eventType());

            if (!unlocked.isEmpty()) {
                log.info(
                    "User {} unlocked {} achievement(s): {}",
                    event.user().getLogin(),
                    unlocked.size(),
                    unlocked.stream().map(AchievementDefinition::getId).toList()
                );
            }
        } catch (Exception e) {
            // Log but don't rethrow - achievements are non-critical
            log.error(
                "Failed to evaluate achievements: userId={}, eventType={}",
                event.user().getId(),
                event.eventType(),
                e
            );
        }
    }
}

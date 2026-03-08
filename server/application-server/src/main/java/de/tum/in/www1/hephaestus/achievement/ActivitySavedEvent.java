package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

import java.time.Instant;

/**
 * Spring application event published when an activity event is successfully recorded.
 *
 * <p>This event is used to decouple the activity module from the achievement module.
 * The achievement system listens for these events to evaluate whether users have
 * unlocked new achievements.
 *
 * @param user the user who performed the activity (may be null for system events)
 * @param eventType the type of activity that was recorded
 * @param occurredAt when the activity actually occurred (source timestamp, not ingestion time)
 * @param workspaceId the workspace where the activity occurred
 * @param targetType the type of the target entity
 * @param targetId the ID of the target entity
 *
 * @see AchievementEventListener
 * @see de.tum.in.www1.hephaestus.activity.ActivityEventService
 */
public record ActivitySavedEvent(
    User user,
    ActivityEventType eventType,
    Instant occurredAt,
    Long workspaceId,
    ActivityTargetType targetType,
    Long targetId
) {
    /**
     * Check if this event has a valid user for achievement evaluation.
     *
     * <p>System events (null user) cannot trigger achievements since
     * achievements are always attributed to a specific user.
     */
    public boolean hasUser() {
        return user != null;
    }
}

package de.tum.in.www1.hephaestus.activity;

/**
 * Projection for activity breakdown by type.
 *
 * <p>Used by {@link ActivityEventRepository} to return counts per activity type
 * for stats display. Consumed by the leaderboard service for breakdown stats.
 */
public interface ActivityBreakdownProjection {
    /** The user who performed the activities */
    Long getActorId();

    /** The type of activity */
    ActivityEventType getEventType();

    /** Number of events of this type */
    Long getCount();

    /** Total XP earned for this event type */
    Double getExperiencePoints();
}

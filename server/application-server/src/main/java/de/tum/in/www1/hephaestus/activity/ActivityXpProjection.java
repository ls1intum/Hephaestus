package de.tum.in.www1.hephaestus.activity;

/**
 * Projection for XP aggregation queries on activity events.
 *
 * <p>Used by {@link ActivityEventRepository} to return aggregated XP
 * without loading full entities. Consumed by the leaderboard service
 * for scoring calculations.
 */
public interface ActivityXpProjection {
    /** The user who earned the XP */
    Long getActorId();

    /** Total XP earned in the timeframe */
    Double getTotalExperiencePoints();

    /** Number of activity events */
    Long getEventCount();
}

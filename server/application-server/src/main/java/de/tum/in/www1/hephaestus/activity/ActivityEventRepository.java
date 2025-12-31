package de.tum.in.www1.hephaestus.activity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Activity event repository with leaderboard aggregation queries.
 *
 * <p>The leaderboard reads pre-computed XP from this table instead of
 * recalculating on-the-fly. This is the single source of truth for XP.
 */
@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    /** Check for idempotent upsert */
    boolean existsByWorkspaceIdAndEventKey(Long workspaceId, String eventKey);

    // ========================================================================
    // Leaderboard Aggregation Queries
    // ========================================================================

    /**
     * Aggregate XP by actor for leaderboard.
     *
     * @param workspaceId the workspace
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return aggregated XP per actor
     */
    @Query(
        """
        SELECT e.actor.id as actorId,
               SUM(e.xp) as totalExperiencePoints,
               COUNT(e) as eventCount
        FROM ActivityEvent e
        WHERE e.workspace.id = :workspaceId
        AND e.actor IS NOT NULL
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        GROUP BY e.actor.id
        """
    )
    List<ActivityXpProjection> findExperiencePointsByWorkspaceAndTimeframe(
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Aggregate XP by actor for leaderboard, filtered by teams.
     *
     * @param workspaceId the workspace
     * @param teamIds set of team IDs to filter by
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return aggregated XP per actor in the specified teams
     */
    @Query(
        """
        SELECT e.actor.id as actorId,
               SUM(e.xp) as totalExperiencePoints,
               COUNT(e) as eventCount
        FROM ActivityEvent e
        JOIN e.actor.teamMemberships tm
        WHERE e.workspace.id = :workspaceId
        AND e.actor IS NOT NULL
        AND tm.team.id IN :teamIds
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        GROUP BY e.actor.id
        """
    )
    List<ActivityXpProjection> findExperiencePointsByWorkspaceAndTeamsAndTimeframe(
        @Param("workspaceId") Long workspaceId,
        @Param("teamIds") Set<Long> teamIds,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Activity breakdown by type for leaderboard stats display.
     *
     * @param workspaceId the workspace
     * @param actorIds actors to get breakdown for
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return breakdown by actor and event type
     */
    @Query(
        """
        SELECT e.actor.id as actorId,
               e.eventType as eventType,
               COUNT(e) as count,
               SUM(e.xp) as experiencePoints
        FROM ActivityEvent e
        WHERE e.workspace.id = :workspaceId
        AND e.actor.id IN :actorIds
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        GROUP BY e.actor.id, e.eventType
        """
    )
    List<ActivityBreakdownProjection> findActivityBreakdown(
        @Param("workspaceId") Long workspaceId,
        @Param("actorIds") Set<Long> actorIds,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    // ========================================================================
    // Existing Queries
    // ========================================================================

    /** Events for a workspace in time range */
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.workspace.id = :workspaceId
        AND e.occurredAt >= :since
        ORDER BY e.occurredAt DESC
        """
    )
    List<ActivityEvent> findByWorkspace(@Param("workspaceId") Long workspaceId, @Param("since") Instant since);

    /** Mentor context: recent activity for a user */
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.actor.id = :actorId
        AND e.occurredAt >= :since
        ORDER BY e.occurredAt DESC
        """
    )
    List<ActivityEvent> findByActor(@Param("actorId") Long actorId, @Param("since") Instant since);

    /** Email attribution: get event chain by correlation ID */
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.correlationId = :correlationId
        ORDER BY e.occurredAt ASC
        """
    )
    List<ActivityEvent> findByCorrelationId(@Param("correlationId") UUID correlationId);

    // ========================================================================
    // Profile XP Lookups
    // ========================================================================

    /**
     * Fetch XP for specific target entities by their IDs and types.
     *
     * <p>Used by the profile module to look up pre-computed XP for individual
     * reviews/comments instead of recalculating on-the-fly.
     *
     * @param workspaceId the workspace
     * @param targetIds set of target entity IDs (review IDs, comment IDs)
     * @param targetTypes set of target types to filter by
     * @return XP indexed by target ID
     */
    @Query(
        """
        SELECT e.targetId as targetId, e.xp as xp
        FROM ActivityEvent e
        WHERE e.workspace.id = :workspaceId
        AND e.targetId IN :targetIds
        AND e.targetType IN :targetTypes
        """
    )
    List<TargetXpProjection> findXpByTargetIdsAndTypesInternal(
        @Param("workspaceId") Long workspaceId,
        @Param("targetIds") Set<Long> targetIds,
        @Param("targetTypes") Set<String> targetTypes
    );

    /**
     * Type-safe overload for fetching XP by target IDs and types.
     *
     * @param workspaceId the workspace
     * @param targetIds set of target entity IDs (review IDs, comment IDs)
     * @param targetTypes set of target types (type-safe enum)
     * @return XP indexed by target ID
     */
    default List<TargetXpProjection> findXpByTargetIdsAndTypes(
        Long workspaceId,
        Set<Long> targetIds,
        Set<ActivityTargetType> targetTypes
    ) {
        Set<String> typeValues = targetTypes
            .stream()
            .map(ActivityTargetType::getValue)
            .collect(java.util.stream.Collectors.toSet());
        return findXpByTargetIdsAndTypesInternal(workspaceId, targetIds, typeValues);
    }

    /**
     * Projection for target-specific XP lookup.
     */
    interface TargetXpProjection {
        Long getTargetId();
        Double getXp();
    }
}

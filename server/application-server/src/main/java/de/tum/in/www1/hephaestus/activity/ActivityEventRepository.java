package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        AND (e.repository IS NULL OR NOT EXISTS (
            SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = e.repository
            AND wtrs.workspace.id = :workspaceId
            AND wtrs.hiddenFromContributions = true
        ))
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
        AND (e.repository IS NULL OR NOT EXISTS (
            SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = e.repository
            AND wtrs.workspace.id = :workspaceId
            AND wtrs.team.id IN :teamIds
            AND wtrs.hiddenFromContributions = true
        ))
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
        AND (e.repository IS NULL OR NOT EXISTS (
            SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = e.repository
            AND wtrs.workspace.id = :workspaceId
            AND wtrs.hiddenFromContributions = true
        ))
        GROUP BY e.actor.id, e.eventType
        """
    )
    List<ActivityBreakdownProjection> findActivityBreakdown(
        @Param("workspaceId") Long workspaceId,
        @Param("actorIds") Set<Long> actorIds,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Count DISTINCT pull requests reviewed by each actor.
     *
     * <p>This query counts unique PRs via the reviews, not event counts.
     * Joins through activity events to reviews to get the distinct PR IDs.
     *
     * @param workspaceId the workspace
     * @param actorIds actors to count for
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return map of actor ID to distinct PR count
     */
    @Query(
        """
        SELECT e.actor.id as actorId, COUNT(DISTINCT r.pullRequest.id) as prCount
        FROM ActivityEvent e
        JOIN PullRequestReview r ON r.id = e.targetId
        WHERE e.workspace.id = :workspaceId
        AND e.actor.id IN :actorIds
        AND e.targetType = 'review'
        AND e.eventType IN (
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_APPROVED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_CHANGES_REQUESTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_COMMENTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_UNKNOWN
        )
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        AND (e.repository IS NULL OR NOT EXISTS (
            SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = e.repository
            AND wtrs.workspace.id = :workspaceId
            AND wtrs.hiddenFromContributions = true
        ))
        GROUP BY e.actor.id
        """
    )
    List<DistinctPrCountProjection> findDistinctReviewedPullRequestCountsByActors(
        @Param("workspaceId") Long workspaceId,
        @Param("actorIds") Set<Long> actorIds,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Helper method to convert projection list to a map.
     */
    default Map<Long, Long> countDistinctReviewedPullRequestsByActors(
        Long workspaceId,
        Set<Long> actorIds,
        Instant since,
        Instant until
    ) {
        return findDistinctReviewedPullRequestCountsByActors(workspaceId, actorIds, since, until)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    DistinctPrCountProjection::getActorId,
                    DistinctPrCountProjection::getPrCount
                )
            );
    }

    /**
     * Projection for distinct PR count by actor.
     */
    interface DistinctPrCountProjection {
        Long getActorId();
        Long getPrCount();
    }

    // ========================================================================
    // Existing Queries
    // ========================================================================

    /** Events for a workspace in time range with limit for safety */
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.workspace.id = :workspaceId
        AND e.occurredAt >= :since
        ORDER BY e.occurredAt DESC
        LIMIT :limit
        """
    )
    List<ActivityEvent> findByWorkspaceWithLimit(
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("limit") int limit
    );

    /** Events for a workspace in time range - default limit 1000 */
    default List<ActivityEvent> findByWorkspace(Long workspaceId, Instant since) {
        return findByWorkspaceWithLimit(workspaceId, since, 1000);
    }

    /**
     * Mentor context: recent activity for a user with limit for safety.
     *
     * <p>Workspace-agnostic: Used by AI mentor which operates with actor context
     * already established. The mentor session is user-specific, not workspace-scoped.
     */
    @WorkspaceAgnostic("Mentor context query - actor ID implies user scope, not tenant boundary")
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.actor.id = :actorId
        AND e.occurredAt >= :since
        ORDER BY e.occurredAt DESC
        LIMIT :limit
        """
    )
    List<ActivityEvent> findByActorWithLimit(
        @Param("actorId") Long actorId,
        @Param("since") Instant since,
        @Param("limit") int limit
    );

    /** Mentor context: recent activity for a user - default limit 100 */
    default List<ActivityEvent> findByActor(Long actorId, Instant since) {
        return findByActorWithLimit(actorId, since, 100);
    }

    /**
     * Email attribution: get event chain by correlation ID.
     * Limited to 100 events per correlation chain (safety bound).
     *
     * <p>Workspace-agnostic: Correlation IDs are globally unique UUIDs. Events in the
     * same correlation chain belong to the same workspace by design.
     */
    @WorkspaceAgnostic("Correlation IDs are globally unique - workspace implicit in the chain")
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.correlationId = :correlationId
        ORDER BY e.occurredAt ASC
        LIMIT 100
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

    // ========================================================================
    // Integrity Verification Queries
    // ========================================================================

    /**
     * Find a random sample of events for integrity verification.
     *
     * <p>Uses ORDER BY RANDOM() with LIMIT for random sampling.
     * This is less efficient than TABLESAMPLE on very large tables but
     * works without requiring the tsm_system_rows extension.
     *
     * <p>Workspace-agnostic: System-wide integrity verification operation.
     * Used by admin/system jobs to spot-check data consistency.
     *
     * @param limit maximum number of events to return
     * @return random sample of events
     */
    @WorkspaceAgnostic("System-wide integrity verification - admin operation")
    @Query(value = "SELECT * FROM activity_event ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<ActivityEvent> findRandomSample(@Param("limit") int limit);
}

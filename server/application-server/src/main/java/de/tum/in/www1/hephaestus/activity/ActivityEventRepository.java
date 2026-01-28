package de.tum.in.www1.hephaestus.activity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity event repository with leaderboard aggregation queries.
 *
 * <p>The leaderboard reads pre-computed XP from this table instead of
 * recalculating on-the-fly. This is the single source of truth for XP.
 *
 * <p><strong>Time range convention:</strong> All timeframe queries use half-open intervals
 * [since, until) - inclusive start, exclusive end. This is the standard convention for
 * time ranges and ensures no events are double-counted or missed at interval boundaries.
 */
@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    /** Check for idempotent upsert */
    boolean existsByWorkspaceIdAndEventKey(Long workspaceId, String eventKey);

    // ========================================================================
    // Leaderboard Aggregation Queries
    // ========================================================================

    /**
     * Aggregate XP by actor for workspace-level leaderboard.
     *
     * <p>This query does NOT filter by hidden repo settings because that is a
     * team-specific setting. The workspace leaderboard shows all activity across
     * the entire workspace. Use {@link #findExperiencePointsByWorkspaceAndTeamsAndTimeframe}
     * for team-filtered results that respect per-team hidden repo settings.
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
        AND e.actor.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
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
     * <p>This query includes label filtering for review-related events. For each team:
     * <ul>
     *   <li>If a team has NO label filters configured for the repository, all events are included</li>
     *   <li>If a team HAS label filters, only events for PRs with at least one matching label are included</li>
     * </ul>
     *
     * <p>Label filtering is applied to review events (targetType = 'review') by joining through
     * PullRequestReview to PullRequest to check labels. Non-review events are not label-filtered.
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
        AND e.actor.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
        AND tm.team.id IN :teamIds
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        AND (e.repository IS NULL OR EXISTS (
            SELECT 1 FROM TeamRepositoryPermission trp
            WHERE trp.repository = e.repository
            AND trp.team.id IN :teamIds
        ))
        AND (e.repository IS NULL OR NOT EXISTS (
            SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
            WHERE wtrs.repository = e.repository
            AND wtrs.workspace.id = :workspaceId
            AND wtrs.team.id IN :teamIds
            AND wtrs.hiddenFromContributions = true
        ))
        AND (
            e.targetType <> 'review'
            OR EXISTS (
                SELECT 1 FROM PullRequestReview prr
                WHERE prr.id = e.targetId
                AND (
                    NOT EXISTS (
                        SELECT 1 FROM WorkspaceTeamLabelFilter wtlf
                        WHERE wtlf.workspace.id = :workspaceId
                        AND wtlf.team.id IN :teamIds
                        AND wtlf.label.repository = prr.pullRequest.repository
                    )
                    OR EXISTS (
                        SELECT 1 FROM WorkspaceTeamLabelFilter wtlf
                        JOIN wtlf.label lbl
                        WHERE wtlf.workspace.id = :workspaceId
                        AND wtlf.team.id IN :teamIds
                        AND wtlf.label.repository = prr.pullRequest.repository
                        AND lbl MEMBER OF prr.pullRequest.labels
                    )
                )
            )
        )
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
     * Activity breakdown by type for workspace-level leaderboard stats display.
     *
     * <p>This query does NOT filter by hidden repo settings because that is a
     * team-specific setting. The workspace leaderboard shows all activity.
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
        AND e.actor.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
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

    /**
     * Count DISTINCT pull requests reviewed by each actor for workspace-level leaderboard.
     *
     * <p>This query counts unique PRs via the reviews, not event counts.
     * Joins through activity events to reviews to get the distinct PR IDs.
     *
     * <p>Does NOT filter by hidden repo settings because that is a team-specific setting.
     * The workspace leaderboard shows all activity.
     *
     * <p><strong>Self-review exclusion:</strong> PRs where the reviewer is also the
     * PR author are excluded from the count.
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
        AND e.actor.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
        AND e.targetType = 'review'
        AND e.eventType IN (
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_APPROVED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_CHANGES_REQUESTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_COMMENTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_UNKNOWN
        )
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        AND (r.pullRequest.author IS NULL OR r.pullRequest.author.id <> e.actor.id)
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
     * Fetch DISTINCT pull request IDs reviewed by a single actor for profile display.
     *
     * <p>Unlike leaderboard queries, this does NOT filter by hidden repo settings because
     * the profile is a personal activity log showing all work done by the user.
     *
     * <p>Self-review exclusion is kept since reviewing your own PR is not meaningful
     * review activity (it's just the PR author looking at their own work).
     *
     * @param workspaceId the workspace
     * @param actorId the actor to get reviewed PRs for
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return list of distinct PullRequest IDs reviewed
     */
    @Query(
        """
        SELECT DISTINCT r.pullRequest.id
        FROM ActivityEvent e
        JOIN PullRequestReview r ON r.id = e.targetId
        WHERE e.workspace.id = :workspaceId
        AND e.actor.id = :actorId
        AND e.actor.type = de.tum.in.www1.hephaestus.gitprovider.user.User$Type.USER
        AND e.targetType = 'review'
        AND e.eventType IN (
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_APPROVED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_CHANGES_REQUESTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_COMMENTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_UNKNOWN
        )
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        AND (r.pullRequest.author IS NULL OR r.pullRequest.author.id <> e.actor.id)
        """
    )
    List<Long> findDistinctReviewedPullRequestIdsByActor(
        @Param("workspaceId") Long workspaceId,
        @Param("actorId") Long actorId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Projection for distinct PR count by actor.
     */
    interface DistinctPrCountProjection {
        Long getActorId();
        Long getPrCount();
    }

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

    /**
     * Deletes all activity events for a workspace.
     * Used during workspace purge to clean up activity data.
     *
     * @param workspaceId the workspace ID
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM activity_event WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);

    // ========================================================================
    // Profile Activity Queries (ActivityEvent as source of truth)
    // ========================================================================

    /**
     * Finds all activity events for a specific actor in a timeframe, scoped to a workspace.
     *
     * <p>Used by the profile module to show a user's complete activity history.
     * Unlike leaderboard queries, this does NOT filter by hidden repo settings because:
     * <ul>
     *   <li>The profile is a personal activity log, not a competition</li>
     *   <li>Users should see all their work regardless of team-level hiding settings</li>
     *   <li>"Hidden from contributions" is for team leaderboard ranking, not personal visibility</li>
     * </ul>
     *
     * <p>Filters for review-related and comment-related events only, excluding PR events
     * which are not displayed in the profile's review activity section.
     *
     * @param workspaceId the workspace to scope to
     * @param actorId the actor (user) to get events for
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return activity events for the actor, ordered by occurrence time descending
     */
    @Query(
        """
        SELECT e
        FROM ActivityEvent e
        LEFT JOIN FETCH e.actor
        LEFT JOIN FETCH e.repository
        WHERE e.workspace.id = :workspaceId
        AND e.actor.id = :actorId
        AND e.occurredAt >= :since
        AND e.occurredAt < :until
        AND e.eventType IN (
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_APPROVED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_CHANGES_REQUESTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_COMMENTED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_UNKNOWN,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.REVIEW_DISMISSED,
            de.tum.in.www1.hephaestus.activity.ActivityEventType.COMMENT_CREATED
        )
        ORDER BY e.occurredAt DESC
        """
    )
    List<ActivityEvent> findProfileActivityByActorInTimeframe(
        @Param("workspaceId") Long workspaceId,
        @Param("actorId") Long actorId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );
}

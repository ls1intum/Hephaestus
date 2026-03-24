package de.tum.in.www1.hephaestus.practices.finding.feedback;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable finding feedback with append-only semantics.
 *
 * <p>Workspace-agnostic: feedback is scoped through
 * {@code FindingFeedback.finding → PracticeFinding.practice → Practice.workspace}.
 */
@Repository
@WorkspaceAgnostic("Feedback scoped through PracticeFinding -> Practice.workspace relationship")
public interface FindingFeedbackRepository extends JpaRepository<FindingFeedback, UUID> {
    /**
     * Returns the most recent feedback for a specific finding by a specific contributor.
     */
    Optional<FindingFeedback> findFirstByFindingIdAndContributorIdOrderByCreatedAtDesc(
        UUID findingId,
        Long contributorId
    );

    /**
     * Returns the latest feedback per finding for a given contributor, using PostgreSQL's
     * {@code DISTINCT ON} for efficient "latest row per group" retrieval.
     *
     * <p>Used to enrich finding lists with the contributor's current feedback state.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (ff.finding_id) ff.*
        FROM finding_feedback ff
        WHERE ff.finding_id IN (:findingIds)
          AND ff.contributor_id = :contributorId
        ORDER BY ff.finding_id, ff.created_at DESC
        """,
        nativeQuery = true
    )
    List<FindingFeedback> findLatestByFindingIdsAndContributor(
        @Param("findingIds") Collection<UUID> findingIds,
        @Param("contributorId") Long contributorId
    );

    /**
     * Engagement statistics: count of feedback actions grouped by action type,
     * scoped to a specific workspace through the finding → practice → workspace chain.
     *
     * @see ActionCountProjection
     */
    @Query(
        """
        SELECT ff.action AS action, COUNT(ff) AS count
        FROM FindingFeedback ff
        JOIN ff.finding f
        JOIN f.practice p
        WHERE ff.contributorId = :contributorId
          AND p.workspace.id = :workspaceId
        GROUP BY ff.action
        """
    )
    List<ActionCountProjection> countByContributorAndWorkspaceGroupByAction(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Projection for feedback action counts used in engagement statistics.
     */
    interface ActionCountProjection {
        FindingFeedbackAction getAction();

        Long getCount();
    }
}

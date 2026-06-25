package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable feedback reaction with append-only semantics.
 *
 * <p>Workspace-agnostic: reaction is scoped through {@code Reaction.feedback → Feedback.workspaceId}.
 */
@Repository
@WorkspaceAgnostic("Reaction scoped through Feedback.workspaceId relationship")
public interface FindingReactionRepository extends JpaRepository<Reaction, UUID> {
    /**
     * Returns the most recent reaction for a specific feedback unit by a specific reactor.
     */
    Optional<Reaction> findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDesc(UUID feedbackId, Long reactorUserId);

    /**
     * Returns the latest reaction per feedback unit for a given reactor, using PostgreSQL's
     * {@code DISTINCT ON} for efficient "latest row per group" retrieval.
     *
     * <p>Used to enrich feedback lists with the reactor's current reaction state.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (ff.feedback_id) ff.*
        FROM reaction ff
        WHERE ff.feedback_id IN (:feedbackIds)
          AND ff.reactor_user_id = :reactorUserId
        ORDER BY ff.feedback_id, ff.created_at DESC
        """,
        nativeQuery = true
    )
    List<Reaction> findLatestByFeedbackIdsAndReactor(
        @Param("feedbackIds") Collection<UUID> feedbackIds,
        @Param("reactorUserId") Long reactorUserId
    );

    /**
     * Latest reaction per {@code recurrence_key} (stable locus) for the given keys, restricted to one
     * reacting developer (the feedback's recipient — only the recipient may react). Used by B2 to suppress
     * re-nagging a locus the student already DISPUTED / marked NOT_APPLICABLE on an earlier run,
     * even though the per-run feedback row (and its {@code feedback_id}) is different this run.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (fr.recurrence_key) fr.*
        FROM reaction fr
        WHERE fr.recurrence_key IN (:findingFingerprints)
          AND fr.reactor_user_id = :reactorUserId
        ORDER BY fr.recurrence_key, fr.created_at DESC
        """,
        nativeQuery = true
    )
    List<Reaction> findLatestByFindingFingerprintsAndDeveloper(
        @Param("findingFingerprints") Collection<String> findingFingerprints,
        @Param("reactorUserId") Long reactorUserId
    );

    /**
     * Engagement statistics: count of reaction actions grouped by action type,
     * scoped to a specific workspace through the feedback → workspace relationship.
     *
     * @see ActionCountProjection
     */
    @Query(
        """
        SELECT ff.action AS action, COUNT(ff) AS count
        FROM Reaction ff
        JOIN ff.feedback fb
        WHERE ff.reactorUserId = :reactorUserId
          AND fb.workspaceId = :workspaceId
        GROUP BY ff.action
        """
    )
    List<ActionCountProjection> countByDeveloperAndWorkspaceGroupByAction(
        @Param("reactorUserId") Long reactorUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Projection for reaction action counts used in engagement statistics.
     */
    interface ActionCountProjection {
        FindingReactionAction getAction();

        Long getCount();
    }
}

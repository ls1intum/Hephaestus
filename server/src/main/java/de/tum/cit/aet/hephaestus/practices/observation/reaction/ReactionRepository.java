package de.tum.cit.aet.hephaestus.practices.observation.reaction;

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
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {
    /**
     * Returns the most recent reaction for a specific feedback unit by a specific reactor. The {@code id}
     * tiebreak makes "latest" deterministic when two append-only submits collide on {@code created_at}.
     */
    Optional<Reaction> findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDescIdDesc(
        UUID feedbackId,
        Long reactorUserId
    );

    /**
     * Latest reaction per {@code recurrence_key} (stable locus) for the given keys, restricted to one
     * reacting developer (the feedback's recipient — only the recipient may react). Used by B2 to suppress
     * re-nagging a locus the student already DISPUTED / marked NOT_APPLICABLE on an earlier run,
     * even though the per-run feedback row (and its {@code feedback_id}) is different this run.
     *
     * <p>Not workspace-joined, and that is safe: the {@code recurrence_key} embeds {@code artifactType} +
     * {@code artifactId}, and {@code artifactId} is the GLOBAL PR/Issue primary key (one identity sequence
     * across all workspaces), so a key resolves to exactly one artifact in exactly one workspace — two
     * workspaces cannot share one. The reactor scope already pins the recipient.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (r.recurrence_key) r.*
        FROM reaction r
        WHERE r.recurrence_key IN (:recurrenceKeys)
          AND r.reactor_user_id = :reactorUserId
        ORDER BY r.recurrence_key, r.created_at DESC, r.id DESC
        """,
        nativeQuery = true
    )
    List<Reaction> findLatestByRecurrenceKeysAndReactor(
        @Param("recurrenceKeys") Collection<String> recurrenceKeys,
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
        SELECT r.action AS action, COUNT(r) AS count
        FROM Reaction r
        JOIN r.feedback fb
        WHERE r.reactorUserId = :reactorUserId
          AND fb.workspaceId = :workspaceId
        GROUP BY r.action
        """
    )
    List<ActionCountProjection> countByReactorAndWorkspaceGroupByAction(
        @Param("reactorUserId") Long reactorUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Projection for reaction action counts used in engagement statistics.
     */
    interface ActionCountProjection {
        ReactionAction getAction();

        Long getCount();
    }
}

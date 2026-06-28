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
     *
     * <p><b>Precondition:</b> the caller MUST pass a non-empty {@code recurrenceKeys}. This is a native
     * query: an empty collection renders {@code IN ()}, which Postgres rejects as a syntax error at
     * execution time (it does NOT return an empty result like a JPQL {@code IN} would). Short-circuit
     * upstream when there are no keys (see {@code ReactionSuppressionFilter}'s empty-key guard).
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
     * Engagement statistics: count of reaction actions grouped by action type, scoped to a workspace through
     * the feedback → workspace relationship.
     *
     * <p>Reaction is {@code @Immutable} and append-only: a developer changing their mind appends a NEW row, and
     * only the LATEST row per {@code feedback_id} is the current reaction (see
     * {@link #findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDescIdDesc}). Counting every historical row
     * would double-count a feedback unit the developer reacted to more than once, inflating the uptake ratio.
     * So collapse to the latest row per {@code feedback_id} ({@code DISTINCT ON … ORDER BY created_at DESC,
     * id DESC}) BEFORE grouping. Native because {@code DISTINCT ON} is Postgres-specific.
     *
     * @see ActionCountProjection
     */
    @Query(
        value = """
        SELECT latest.action AS action, COUNT(*) AS count
        FROM (
            SELECT DISTINCT ON (r.feedback_id) r.action AS action
            FROM reaction r
            JOIN feedback fb ON fb.id = r.feedback_id
            WHERE r.reactor_user_id = :reactorUserId
              AND fb.workspace_id = :workspaceId
            ORDER BY r.feedback_id, r.created_at DESC, r.id DESC
        ) latest
        GROUP BY latest.action
        """,
        nativeQuery = true
    )
    List<ActionCountProjection> countByReactorAndWorkspaceGroupByAction(
        @Param("reactorUserId") Long reactorUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Projection for reaction action counts used in engagement statistics. {@code action} is the stored enum
     * STRING (the native query selects the raw column); the caller maps it back via {@link ReactionAction}.
     */
    interface ActionCountProjection {
        String getAction();

        Long getCount();
    }
}

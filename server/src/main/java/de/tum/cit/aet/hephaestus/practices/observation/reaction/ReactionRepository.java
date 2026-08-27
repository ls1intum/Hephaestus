package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable feedback reaction with append-only semantics.
 *
 * <p>Workspace-agnostic: reaction is scoped through {@code Reaction.feedback → Feedback.workspaceId}.
 *
 * <p>Every read here answers "what does this recipient currently say", and none of them does it by taking the
 * newest row. A row is a delta over two independent optional dimensions ({@link Reaction}), so the current
 * usefulness and the current resolution can come from different rows, and a withdrawal row ends everything
 * before it. That rule is written once as {@link #STILL_SPEAKS} and reused; a query that forgets it reports
 * an answer the recipient has already taken back.
 */
@Repository
@WorkspaceAgnostic("Reaction scoped through Feedback.workspaceId relationship")
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {
    /**
     * Restricts {@code r} to the rows that still speak for its reactor: those newer than their most recent
     * withdrawal of that same piece of feedback, or all of them when they never withdrew.
     *
     * <p>A withdrawal is a row with neither dimension set, so the condition reads "no withdrawal is newer than
     * this row". Ordering on {@code (created_at, id)} rather than {@code created_at} alone matters because two
     * appends can share a timestamp, and the tie has to break the same way here as in the {@code ORDER BY}
     * that picks the winner — otherwise a withdrawal written in the same instant as the answer it retracts
     * could fail to retract it.
     *
     * <p>Binds to the alias {@code r}, so every query using it must name the reaction row that way.
     */
    String STILL_SPEAKS = """
              AND NOT EXISTS (
                  SELECT 1 FROM reaction w
                   WHERE w.feedback_id = r.feedback_id
                     AND w.reactor_user_id = r.reactor_user_id
                     AND w.usefulness IS NULL
                     AND w.action IS NULL
                     AND (w.created_at, w.id) > (r.created_at, r.id))
        """;

    /**
     * The recipient's current answer to one piece of feedback, folded across rows.
     *
     * <p>Two independent lookups rather than one row: someone who rated a unit helpful and later disputed it
     * said both things, and the newest row alone would report only the second. Returns no row at all when
     * they have said nothing, or nothing since their last withdrawal.
     */
    @Query(
        value = """
            SELECT
                (SELECT r.usefulness FROM reaction r
                  WHERE r.feedback_id = :feedbackId AND r.reactor_user_id = :reactorUserId
                    AND r.usefulness IS NOT NULL
            """ +
            STILL_SPEAKS +
            """
                  ORDER BY r.created_at DESC, r.id DESC LIMIT 1) AS "usefulness",
                (SELECT r.action FROM reaction r
                  WHERE r.feedback_id = :feedbackId AND r.reactor_user_id = :reactorUserId
                    AND r.action IS NOT NULL
            """ +
            STILL_SPEAKS +
            """
                  ORDER BY r.created_at DESC, r.id DESC LIMIT 1) AS "resolution",
                (SELECT r.explanation FROM reaction r
                  WHERE r.feedback_id = :feedbackId AND r.reactor_user_id = :reactorUserId
                    AND r.explanation IS NOT NULL
            """ +
            STILL_SPEAKS +
            """
                  ORDER BY r.created_at DESC, r.id DESC LIMIT 1) AS "comment",
                (SELECT MAX(r.created_at) FROM reaction r
                  WHERE r.feedback_id = :feedbackId AND r.reactor_user_id = :reactorUserId
                    AND (r.usefulness IS NOT NULL OR r.action IS NOT NULL)
            """ +
            STILL_SPEAKS +
            """
                ) AS "respondedAt"
            """,
        nativeQuery = true
    )
    Optional<CurrentResponseProjection> findCurrentResponse(
        @Param("feedbackId") UUID feedbackId,
        @Param("reactorUserId") Long reactorUserId
    );

    /**
     * The current answer to one piece of feedback. Every component is null when the recipient has said nothing
     * that still stands, which is how the caller tells "no response" from "a response with one dimension".
     */
    interface CurrentResponseProjection {
        @Nullable
        String getUsefulness();

        @Nullable
        String getResolution();

        @Nullable
        String getComment();

        @Nullable
        Instant getRespondedAt();
    }

    /**
     * Current resolution per {@code recurrence_key} (stable locus) for the given keys, restricted to one
     * reacting developer (the feedback's recipient — only the recipient may react). Used by reaction
     * suppression to suppress re-nagging a locus the student already DISPUTED / marked NOT_APPLICABLE on an
     * earlier run, even though the per-run feedback row (and its {@code feedback_id}) is different this run.
     *
     * <p>Rows carrying no resolution are skipped rather than ending the search: a recipient who rated a unit
     * helpful after disputing it did not un-dispute it, and a suppression that read the newest row would
     * start re-nagging on the strength of a thumbs-up. Taking the answer back — a withdrawal row — does end
     * it, which is the point of {@link #STILL_SPEAKS}.
     *
     * <p>Not workspace-joined, and that is safe: the {@code recurrence_key} embeds {@code artifactKind} +
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
            SELECT DISTINCT ON (r.recurrence_key) r.recurrence_key AS "recurrenceKey", r.action AS "resolution"
            FROM reaction r
            WHERE r.recurrence_key IN (:recurrenceKeys)
              AND r.reactor_user_id = :reactorUserId
              AND r.action IS NOT NULL
            """ +
            STILL_SPEAKS +
            """
            ORDER BY r.recurrence_key, r.created_at DESC, r.id DESC
            """,
        nativeQuery = true
    )
    List<LocusResolutionProjection> findCurrentResolutionByRecurrenceKeys(
        @Param("recurrenceKeys") Collection<String> recurrenceKeys,
        @Param("reactorUserId") Long reactorUserId
    );

    /** The resolution that currently stands at one recurrence locus. */
    interface LocusResolutionProjection {
        String getRecurrenceKey();
        String getResolution();
    }

    /**
     * Engagement statistics: how many piece of feedbacks the developer currently resolves each way, scoped to a
     * workspace through the feedback → workspace relationship.
     *
     * <p>Counts units, not rows. A developer who changed their mind appended a second row, and counting both
     * would inflate the uptake ratio — so collapse to the current resolution per {@code feedback_id} before
     * grouping. Native because {@code DISTINCT ON} is Postgres-specific.
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
                  AND r.action IS NOT NULL
            """ +
            STILL_SPEAKS +
            """
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
     * STRING (the native query selects the raw column); the caller maps it back via {@code FeedbackResolution}.
     */
    interface ActionCountProjection {
        String getAction();

        Long getCount();
    }
}

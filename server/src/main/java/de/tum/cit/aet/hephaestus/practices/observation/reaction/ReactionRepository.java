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

/** Persistence for append-only feedback-response snapshots in the legacy {@code reaction} table. */
@Repository
@WorkspaceAgnostic("Reaction scoped through Feedback.workspaceId relationship")
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {
    /** The newest complete response snapshot, including an all-null deletion marker. */
    @Query(
        value = """
        SELECT r.usefulness AS "usefulness", r.action AS "resolution",
               r.explanation AS "comment", r.created_at AS "respondedAt"
        FROM reaction r
        WHERE r.feedback_id = :feedbackId AND r.reactor_user_id = :reactorUserId
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT 1
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

    /** Current resolution for each requested recurrence locus. The caller passes at least one key. */
    @Query(
        value = """
        SELECT DISTINCT ON (o.recurrence_key) o.recurrence_key AS "recurrenceKey", r.action AS "resolution"
        FROM feedback fb
        JOIN feedback_observation fo ON fo.feedback_id = fb.id
        JOIN observation o ON o.id = fo.observation_id
        JOIN LATERAL (
            SELECT response.action, response.created_at, response.id
            FROM reaction response
            WHERE response.feedback_id = fb.id AND response.reactor_user_id = :reactorUserId
            ORDER BY response.created_at DESC, response.id DESC LIMIT 1
        ) r ON r.action IS NOT NULL
        WHERE o.recurrence_key IN (:recurrenceKeys)
          AND fb.workspace_id = :workspaceId
        ORDER BY o.recurrence_key, r.created_at DESC, r.id DESC
        """,
        nativeQuery = true
    )
    List<LocusResolutionProjection> findCurrentResolutionByRecurrenceKeys(
        @Param("recurrenceKeys") Collection<String> recurrenceKeys,
        @Param("reactorUserId") Long reactorUserId,
        @Param("workspaceId") Long workspaceId
    );

    /** The resolution that currently stands at one recurrence locus. */
    interface LocusResolutionProjection {
        String getRecurrenceKey();
        String getResolution();
    }

    /** Resolution counts from each feedback unit's newest response snapshot. */
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
        WHERE latest.action IS NOT NULL
        GROUP BY latest.action
        """,
        nativeQuery = true
    )
    List<ActionCountProjection> countByReactorAndWorkspaceGroupByAction(
        @Param("reactorUserId") Long reactorUserId,
        @Param("workspaceId") Long workspaceId
    );

    /** The legacy {@code action} column contains the response resolution enum name. */
    interface ActionCountProjection {
        String getAction();

        Long getCount();
    }
}

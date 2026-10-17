package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ReviewLineageRepository extends Repository<Feedback, UUID> {

    String RESULT_COLUMNS = """
        SELECT r.id, r.review_id AS "reviewId", r.item_id AS "itemId", r.practice_id AS "practiceId",
               r.summary, p.slug AS "practiceSlug", v.name AS "practiceName", r.outcome, r.severity,
               (SELECT count(*) FROM problem child WHERE child.result_id = r.id
                  AND child.workspace_id = r.workspace_id) AS "problemCount",
               r.recorded_at AS "recordedAt", r.ended_at AS "endedAt",
               (SELECT m.created_at FROM result_mark m WHERE m.result_id = r.id
                 AND m.workspace_id = r.workspace_id AND m.kind = 'INCORRECT'
                 AND m.removed_at IS NULL) AS "markedIncorrectAt",
               (SELECT m.created_at FROM result_mark m WHERE m.result_id = r.id
                 AND m.workspace_id = r.workspace_id AND m.kind = 'ACCEPTED'
                 AND m.removed_at IS NULL) AS "acceptedAt"
        FROM result r JOIN practice p ON p.id = r.practice_id AND p.workspace_id = r.workspace_id
        LEFT JOIN practice_version v ON v.id = r.practice_version_id AND v.workspace_id = r.workspace_id
        """;

    @Query(value = RESULT_COLUMNS + " WHERE r.workspace_id = :workspaceId AND r.id IN (:ids)", nativeQuery = true)
    List<ResultRow> findResults(@Param("workspaceId") long workspaceId, @Param("ids") Collection<UUID> ids);

    @Query(value = RESULT_COLUMNS + """
        WHERE r.workspace_id = :workspaceId AND r.review_id = :reviewId
        ORDER BY r.recorded_at DESC, r.id DESC LIMIT :limit
        """, nativeQuery = true)
    List<ResultRow> findResultsOfReview(
            @Param("workspaceId") long workspaceId, @Param("reviewId") UUID reviewId, @Param("limit") int limit);

    @Query(
            value = "SELECT id FROM result WHERE workspace_id = :workspaceId AND review_id = :reviewId",
            nativeQuery = true)
    List<UUID> findResultIdsOfReview(@Param("workspaceId") long workspaceId, @Param("reviewId") UUID reviewId);

    @Query(
            value = "SELECT count(*) FROM result WHERE workspace_id = :workspaceId AND review_id = :reviewId",
            nativeQuery = true)
    int countResultsOfReview(@Param("workspaceId") long workspaceId, @Param("reviewId") UUID reviewId);

    interface ResultRow {
        UUID getId();

        UUID getReviewId();

        long getItemId();

        long getPracticeId();

        @Nullable
        String getSummary();

        String getPracticeSlug();

        @Nullable
        String getPracticeName();

        String getOutcome();

        @Nullable
        String getSeverity();

        long getProblemCount();

        Instant getRecordedAt();

        @Nullable
        Instant getEndedAt();

        @Nullable
        Instant getMarkedIncorrectAt();

        @Nullable
        Instant getAcceptedAt();
    }

    String FEEDBACK_COLUMNS = """
        SELECT f.id, f.review_id AS "reviewId", f.item_id AS "itemId", f.destination, f.state,
               f.delivered_at AS "deliveredAt", f.replaces_id AS "replacesId", f.created_at AS "createdAt",
               COALESCE((SELECT d.action = 'WITHDRAW' FROM feedback_decision d
                  WHERE d.feedback_id = f.id AND d.workspace_id = f.workspace_id
                    AND d.action IN ('WITHDRAW', 'RESTORE')
                  ORDER BY d.decided_at DESC, d.id DESC LIMIT 1), false) AS withdrawn
        FROM feedback f
        """;

    @Query(value = FEEDBACK_COLUMNS + """
        WHERE f.workspace_id = :workspaceId AND f.id IN (:ids) ORDER BY f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<FeedbackRow> findFeedback(@Param("workspaceId") long workspaceId, @Param("ids") Collection<UUID> ids);

    interface FeedbackRow {
        UUID getId();

        UUID getReviewId();

        @Nullable
        Long getItemId();

        String getDestination();

        String getState();

        @Nullable
        Instant getDeliveredAt();

        @Nullable
        UUID getReplacesId();

        Instant getCreatedAt();

        boolean getWithdrawn();
    }

    @Query(value = """
        SELECT f.id FROM feedback f WHERE f.workspace_id = :workspaceId
          AND EXISTS (SELECT 1 FROM feedback_basis b WHERE b.feedback_id = f.id
            AND b.workspace_id = f.workspace_id AND b.result_id = :resultId)
        ORDER BY f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<UUID> findFeedbackIdsOfResult(@Param("workspaceId") long workspaceId, @Param("resultId") UUID resultId);

    @Query(value = """
        SELECT id FROM feedback WHERE workspace_id = :workspaceId AND review_id = :reviewId
        ORDER BY created_at DESC, id DESC
        """, nativeQuery = true)
    List<UUID> findFeedbackIdsOfReview(@Param("workspaceId") long workspaceId, @Param("reviewId") UUID reviewId);

    @Query(value = """
        SELECT b.feedback_id AS "feedbackId", b.result_id AS "resultId", b.problem_id AS "problemId"
        FROM feedback_basis b JOIN feedback f ON f.id = b.feedback_id AND f.workspace_id = b.workspace_id
        WHERE b.workspace_id = :workspaceId AND b.feedback_id IN (:feedbackIds)
        GROUP BY b.feedback_id, b.result_id, b.problem_id
        ORDER BY b.feedback_id, min(b.ordinal), b.result_id, b.problem_id
        """, nativeQuery = true)
    List<BindingRow> findBindings(
            @Param("workspaceId") long workspaceId, @Param("feedbackIds") Collection<UUID> feedbackIds);

    interface BindingRow {
        UUID getFeedbackId();

        UUID getResultId();

        @Nullable
        UUID getProblemId();
    }

    @Query(value = """
        SELECT id FROM review WHERE workspace_id = :workspaceId AND item_id = :itemId
        ORDER BY ordered_at DESC, admitted_at DESC NULLS LAST, id::text DESC LIMIT 1
        """, nativeQuery = true)
    Optional<UUID> findNewestReviewOfItem(@Param("workspaceId") long workspaceId, @Param("itemId") long itemId);

    @Query("SELECT f FROM Feedback f WHERE f.workspaceId = :workspaceId AND f.id IN :ids")
    List<Feedback> findAllByWorkspaceIdAndIdIn(
            @Param("workspaceId") long workspaceId, @Param("ids") Collection<UUID> ids);
}

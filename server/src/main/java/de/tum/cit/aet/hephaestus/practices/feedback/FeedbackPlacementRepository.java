package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable {@link FeedbackPlacement} rows — where/how a {@link Feedback} unit was
 * physically posted (the SUMMARY comment + each INLINE diff note). Saved via JPA {@code save()} (simple
 * UUID PK + {@code @PrePersist}); the recorder guards against double-writes at the {@link Feedback} level.
 *
 * <p>Workspace-agnostic: a placement carries no tenant column — it is scoped through its parent
 * {@link Feedback} (which holds {@code workspace_id}), so callers tenant-scope at the {@code Feedback} level.
 */
@Repository
@WorkspaceAgnostic("FeedbackPlacement is scoped through its parent Feedback's workspace_id, not its own")
public interface FeedbackPlacementRepository extends JpaRepository<FeedbackPlacement, UUID> {
    List<FeedbackPlacement> findByFeedbackId(UUID feedbackId);

    @Query(
        """
        SELECT p FROM FeedbackPlacement p
        WHERE p.feedback.threadKey = :threadKey
          AND p.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.DELIVERED
          AND p.placementType = de.tum.cit.aet.hephaestus.practices.feedback.PlacementType.SUMMARY
          AND p.postedCommentRef IS NOT NULL
        ORDER BY p.feedback.createdAt DESC
        LIMIT 1
        """
    )
    Optional<FeedbackPlacement> findLatestDeliveredSummary(@Param("threadKey") String threadKey);

    @Query(
        value = """
        SELECT fp.*
        FROM feedback_placement fp
        WHERE fp.feedback_id = :feedbackId
        ORDER BY CASE fp.placement_type
                     WHEN 'SUMMARY' THEN 0
                     WHEN 'INLINE' THEN 1
                     WHEN 'CONVERSATION_TURN' THEN 2
                     ELSE 3
                 END,
                 fp.anchor_path NULLS FIRST,
                 fp.anchor_start_line NULLS FIRST,
                 fp.anchor_end_line NULLS FIRST,
                 fp.created_at,
                 fp.id
        """,
        nativeQuery = true
    )
    List<FeedbackPlacement> findByFeedbackIdInDisplayOrder(@Param("feedbackId") UUID feedbackId);
}

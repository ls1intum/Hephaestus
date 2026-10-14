package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackDecisionRepository extends JpaRepository<FeedbackDecision, UUID> {

    List<FeedbackDecision> findByWorkspaceIdAndFeedbackIdOrderByDecidedAtDescIdDesc(long workspaceId, UUID feedbackId);

    @Query(value = """
        SELECT * FROM feedback_decision WHERE workspace_id = :workspaceId AND feedback_id = :feedbackId
          AND action IN ('APPROVE','REJECT') ORDER BY decided_at DESC, id::text DESC LIMIT 1
        """, nativeQuery = true)
    Optional<FeedbackDecision> findLatestApproval(
            @Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT * FROM feedback_decision WHERE workspace_id = :workspaceId AND feedback_id = :feedbackId
          AND action IN ('WITHDRAW','RESTORE') ORDER BY decided_at DESC, id::text DESC LIMIT 1
        """, nativeQuery = true)
    Optional<FeedbackDecision> findLatestVisibility(
            @Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT DISTINCT ON (feedback_id) * FROM feedback_decision
         WHERE workspace_id = :workspaceId AND feedback_id IN (:feedbackIds) AND action IN ('WITHDRAW','RESTORE')
         ORDER BY feedback_id, decided_at DESC, id::text DESC
        """, nativeQuery = true)
    List<FeedbackDecision> findCurrentVisibilityForFeedbackIds(
            @Param("workspaceId") long workspaceId, @Param("feedbackIds") Collection<UUID> feedbackIds);
}

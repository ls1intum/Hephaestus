package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@WorkspaceAgnostic("Approval rows carry and are always queried with the feedback workspace id")
public interface FeedbackApprovalRepository extends JpaRepository<FeedbackApproval, UUID> {
    Optional<FeedbackApproval> findByFeedbackIdAndWorkspaceId(UUID feedbackId, Long workspaceId);

    @Query(
        value = "SELECT a.workspace_id AS workspaceId, a.feedback_id AS feedbackId FROM feedback_approval a " +
            "JOIN feedback f ON f.id = a.feedback_id AND f.workspace_id = a.workspace_id " +
            "WHERE a.decision = 'APPROVED' " +
            "AND (f.delivery_state = 'PREPARED' OR " +
            "(f.delivery_state = 'PARTIALLY_DELIVERED' AND f.suppression_reason IS NULL)) ORDER BY a.decided_at",
        nativeQuery = true
    )
    List<PendingApproval> findPendingApproved(Pageable pageable);

    interface PendingApproval {
        Long getWorkspaceId();
        UUID getFeedbackId();
    }
}

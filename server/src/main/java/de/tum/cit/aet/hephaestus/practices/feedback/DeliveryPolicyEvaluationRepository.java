package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Immutable evaluations are tenant-scoped by a raw workspace_id")
public interface DeliveryPolicyEvaluationRepository extends JpaRepository<DeliveryPolicyEvaluation, UUID> {
    List<DeliveryPolicyEvaluation> findByWorkspaceIdAndFeedbackIdOrderByEvaluatedAtAsc(
        Long workspaceId,
        UUID feedbackId
    );

    List<DeliveryPolicyEvaluation> findByWorkspaceIdAndAgentJobIdAndFeedbackIdIsNullAndSurfaceOrderByEvaluatedAtAsc(
        Long workspaceId,
        UUID agentJobId,
        DeliveryPolicySurface surface
    );
}

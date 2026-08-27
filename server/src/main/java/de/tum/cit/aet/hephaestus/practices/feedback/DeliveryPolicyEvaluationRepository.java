package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(
        value = """
        DELETE FROM delivery_policy_evaluation evaluation
        USING agent_job job
        WHERE evaluation.workspace_id = :workspaceId
          AND job.workspace_id = evaluation.workspace_id
          AND job.id = evaluation.agent_job_id
          AND job.artifact_kind IN ('scm.pull_request', 'scm.issue')
        """,
        nativeQuery = true
    )
    int deleteScmArtifactEvaluations(@Param("workspaceId") long workspaceId);

    @Modifying
    @Query("DELETE FROM DeliveryPolicyEvaluation evaluation WHERE evaluation.workspaceId = :workspaceId")
    int deleteAllByWorkspaceId(@Param("workspaceId") long workspaceId);

    @Modifying
    @Query(
        value = """
        DELETE FROM delivery_policy_evaluation evaluation
        USING agent_job job
        WHERE evaluation.workspace_id = :workspaceId
          AND job.workspace_id = evaluation.workspace_id
          AND job.id = evaluation.agent_job_id
          AND job.artifact_kind = 'chat.conversation_thread'
          AND (
            EXISTS (
              SELECT 1 FROM feedback
              WHERE feedback.workspace_id = evaluation.workspace_id
                AND feedback.agent_job_id = evaluation.agent_job_id
                AND feedback.artifact_kind = 'chat.conversation_thread'
                AND feedback.artifact_id IN (:threadIds)
            )
            OR EXISTS (
              SELECT 1 FROM observation
              JOIN practice ON practice.id = observation.practice_id
              WHERE practice.workspace_id = evaluation.workspace_id
                AND observation.agent_job_id = evaluation.agent_job_id
                AND observation.artifact_kind = 'chat.conversation_thread'
                AND observation.artifact_id IN (:threadIds)
            )
          )
        """,
        nativeQuery = true
    )
    int deleteConversationEvaluationsForThreads(
        @Param("workspaceId") long workspaceId,
        @Param("threadIds") Collection<Long> threadIds
    );

    @Modifying
    @Query(
        value = """
        DELETE FROM delivery_policy_evaluation evaluation
        USING agent_job job
        WHERE evaluation.workspace_id = :workspaceId
          AND job.workspace_id = evaluation.workspace_id
          AND job.id = evaluation.agent_job_id
          AND job.artifact_kind = 'chat.conversation_thread'
        """,
        nativeQuery = true
    )
    int deleteAllConversationEvaluations(@Param("workspaceId") long workspaceId);

    @Modifying
    @Query(
        value = """
        DELETE FROM delivery_policy_evaluation evaluation
        USING agent_job job
        WHERE evaluation.workspace_id = :workspaceId
          AND job.workspace_id = evaluation.workspace_id
          AND job.id = evaluation.agent_job_id
          AND job.artifact_kind = 'chat.conversation_thread'
          AND (
            EXISTS (
              SELECT 1 FROM feedback
              WHERE feedback.workspace_id = evaluation.workspace_id
                AND feedback.agent_job_id = evaluation.agent_job_id
                AND feedback.artifact_kind = 'chat.conversation_thread'
                AND feedback.about_user_id = :aboutUserId
            )
            OR EXISTS (
              SELECT 1 FROM observation
              JOIN practice ON practice.id = observation.practice_id
              WHERE practice.workspace_id = evaluation.workspace_id
                AND observation.agent_job_id = evaluation.agent_job_id
                AND observation.artifact_kind = 'chat.conversation_thread'
                AND observation.about_user_id = :aboutUserId
            )
          )
        """,
        nativeQuery = true
    )
    int deleteConversationEvaluationsAboutUser(
        @Param("workspaceId") long workspaceId,
        @Param("aboutUserId") long aboutUserId
    );
}

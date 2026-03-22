package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentJobRepository extends JpaRepository<AgentJob, UUID> {
    Page<AgentJob> findByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<AgentJob> findByWorkspaceIdAndStatus(Long workspaceId, AgentJobStatus status, Pageable pageable);

    Optional<AgentJob> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    Page<AgentJob> findByWorkspaceIdAndConfigId(Long workspaceId, Long configId, Pageable pageable);

    Page<AgentJob> findByWorkspaceIdAndStatusAndConfigId(
        Long workspaceId,
        AgentJobStatus status,
        Long configId,
        Pageable pageable
    );

    long countByConfigIdAndStatusIn(Long configId, Collection<AgentJobStatus> statuses);

    List<AgentJob> findByStatus(AgentJobStatus status);

    List<AgentJob> findByStatusIn(Collection<AgentJobStatus> statuses);

    Optional<AgentJob> findByJobTokenHashAndStatus(String jobTokenHash, AgentJobStatus status);

    // ── Execution pipeline queries (issue #746) ──

    /** Idempotency check: find active job with same idempotency key in workspace. */
    Optional<AgentJob> findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
        Long workspaceId,
        String idempotencyKey,
        Collection<AgentJobStatus> statuses
    );

    /**
     * Claim a QUEUED job for execution with {@code FOR UPDATE SKIP LOCKED}.
     * Returns empty if the row is already locked (duplicate NATS delivery) or not QUEUED.
     */
    @WorkspaceAgnostic("ID-based claim; job ID from workspace-scoped NATS event")
    @Query(
        value = "SELECT * FROM agent_job WHERE id = :id AND status = 'QUEUED' FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<AgentJob> findByIdQueuedForUpdateSkipLocked(@Param("id") UUID id);

    /**
     * Conditional terminal status transition. Returns number of rows affected (0 or 1).
     * Used to prevent cancel/executor races from corrupting state.
     */
    @WorkspaceAgnostic("ID-based status transition; job ID from workspace-scoped context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = :newStatus, j.completedAt = :now, j.errorMessage = :error " +
            "WHERE j.id = :id AND j.status IN :fromStatuses"
    )
    int transitionStatus(
        @Param("id") UUID id,
        @Param("newStatus") AgentJobStatus newStatus,
        @Param("now") Instant now,
        @Param("error") String error,
        @Param("fromStatuses") Collection<AgentJobStatus> fromStatuses
    );

    /** Zombie sweeper: find QUEUED jobs older than cutoff (never picked up by NATS consumer). */
    @WorkspaceAgnostic("Cross-workspace zombie recovery; caller is @WorkspaceAgnostic sweeper")
    @Query("SELECT j FROM AgentJob j WHERE j.status = 'QUEUED' AND j.createdAt < :cutoff")
    List<AgentJob> findStaleQueuedJobs(@Param("cutoff") Instant cutoff);

    /** Stale RUNNING reaper: find RUNNING jobs that exceeded their expected lifetime. */
    @WorkspaceAgnostic("Cross-workspace stale job reaper; caller is @WorkspaceAgnostic sweeper")
    @Query("SELECT j FROM AgentJob j WHERE j.status = 'RUNNING' AND j.startedAt < :cutoff")
    List<AgentJob> findStaleRunningJobs(@Param("cutoff") Instant cutoff);

    // ── Delivery tracking (issue #748) ──

    /** Update delivery status and comment ID after feedback posting. */
    @WorkspaceAgnostic("ID-based delivery update; job ID from workspace-scoped delivery context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentJob j SET j.deliveryStatus = :status, j.deliveryCommentId = :commentId " + "WHERE j.id = :id")
    void updateDeliveryStatus(
        @Param("id") UUID id,
        @Param("status") DeliveryStatus status,
        @Param("commentId") String commentId
    );

    /**
     * Conditional delivery status transition (CAS). Returns 1 if transitioned, 0 if the
     * current status didn't match any of the expected {@code fromStatuses}.
     * Used by {@code retryDelivery} to prevent concurrent retry races.
     */
    @WorkspaceAgnostic("ID-based delivery transition; job ID from workspace-scoped context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.deliveryStatus = :newStatus " +
            "WHERE j.id = :id AND j.status = 'COMPLETED' AND j.deliveryStatus IN :fromStatuses"
    )
    int transitionDeliveryStatus(
        @Param("id") UUID id,
        @Param("newStatus") DeliveryStatus newStatus,
        @Param("fromStatuses") Collection<DeliveryStatus> fromStatuses
    );

    /**
     * Find the delivery comment ID from the most recent delivered job for the same PR.
     * Used for re-analysis dedup: update the existing comment instead of creating a duplicate.
     *
     * <p>Note: {@code CAST(:pullRequestId AS text)} is required because the expression index
     * {@code idx_agent_job_delivery_dedup} is defined on {@code (metadata->>'pull_request_id')}
     * which returns text. Without the explicit cast, PostgreSQL may not match the index.
     */
    @WorkspaceAgnostic("Cross-job lookup for re-analysis dedup; workspace-scoped by parameter")
    @Query(
        value = """
        SELECT delivery_comment_id FROM agent_job
        WHERE workspace_id = :workspaceId
        AND job_type = 'PULL_REQUEST_REVIEW'
        AND delivery_comment_id IS NOT NULL
        AND delivery_status = 'DELIVERED'
        AND metadata->>'pull_request_id' = CAST(:pullRequestId AS text)
        AND id != :currentJobId
        ORDER BY completed_at DESC NULLS LAST
        LIMIT 1
        """,
        nativeQuery = true
    )
    Optional<String> findPreviousDeliveryCommentId(
        @Param("workspaceId") Long workspaceId,
        @Param("pullRequestId") Long pullRequestId,
        @Param("currentJobId") UUID currentJobId
    );
}

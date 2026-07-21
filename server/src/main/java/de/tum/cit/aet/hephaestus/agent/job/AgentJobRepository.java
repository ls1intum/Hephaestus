package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
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

    // Execution pipeline queries (issue #746)

    /** Idempotency check: find active job with same idempotency key in workspace. */
    Optional<AgentJob> findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
        Long workspaceId,
        String idempotencyKey,
        Collection<AgentJobStatus> statuses
    );

    /**
     * Cooldown check: find the most recently created job whose idempotency key starts with the
     * given prefix (PR-scoped, config-scoped, but SHA-agnostic) and was created after the cutoff.
     * Used to enforce a minimum interval between reviews for the same PR.
     */
    @Query(
        "SELECT j FROM AgentJob j WHERE j.workspace.id = :workspaceId" +
            " AND j.idempotencyKey LIKE :keyPrefix ESCAPE '\\'" +
            " AND j.createdAt > :cutoff" +
            " ORDER BY j.createdAt DESC" +
            " LIMIT 1"
    )
    Optional<AgentJob> findRecentJobByKeyPrefix(
        @Param("workspaceId") Long workspaceId,
        @Param("keyPrefix") String keyPrefix,
        @Param("cutoff") Instant cutoff
    );

    /**
     * Claim a QUEUED job for execution with {@code FOR UPDATE SKIP LOCKED}.
     * Returns empty if the row is already locked (a concurrent poller claimed it first) or not QUEUED.
     */
    @WorkspaceAgnostic("ID-based claim; job ID from a workspace-scoped candidate poll")
    @Query(
        value = "SELECT * FROM agent_job WHERE id = :id AND status = 'QUEUED' FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<AgentJob> findByIdQueuedForUpdateSkipLocked(@Param("id") UUID id);

    /** Reload a job with its workspace eagerly fetched (avoids LazyInitializationException on sandbox threads). */
    @WorkspaceAgnostic("ID-based reload; job ID from workspace-scoped claim context")
    @Query("SELECT j FROM AgentJob j LEFT JOIN FETCH j.workspace WHERE j.id = :id")
    Optional<AgentJob> findByIdWithWorkspace(@Param("id") UUID id);

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

    /**
     * Terminal transition fenced to the owning worker (#1138): like {@link #transitionStatus} but also
     * requires {@code worker_id = :workerId}, so a worker whose job was orphan-requeued to a sibling
     * cannot clobber the sibling's run with its own late terminal write.
     *
     * @return rows updated (0 or 1)
     */
    @WorkspaceAgnostic("ID-based fenced transition; job ID + owner from worker-local execution context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = :newStatus, j.completedAt = :now, j.errorMessage = :error " +
            "WHERE j.id = :id AND j.status IN :fromStatuses AND j.workerId = :workerId"
    )
    int transitionStatusOwnedBy(
        @Param("id") UUID id,
        @Param("newStatus") AgentJobStatus newStatus,
        @Param("now") Instant now,
        @Param("error") String error,
        @Param("fromStatuses") Collection<AgentJobStatus> fromStatuses,
        @Param("workerId") String workerId
    );

    /**
     * Conditional transition to {@link AgentJobStatus#CANCELLED} that also records the
     * cancellation reason. Used by explicit user-cancel paths and as the drain coordinator's
     * fallback when a worker-fenced requeue ({@link #requeueOrphan}) isn't possible (no worker
     * identity known). Unfenced by worker — callers with a known worker id should prefer
     * {@link #transitionToCancelledOwnedBy} so a belated cancel from a worker that already lost
     * the job (orphan-requeued to a sibling) cannot clobber the sibling's run.
     *
     * @return number of rows updated (0 or 1)
     */
    @WorkspaceAgnostic("ID-based cancel; job ID from worker-local drain or user-scoped admin call")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus.CANCELLED, " +
            "j.completedAt = :now, j.errorMessage = :error, j.cancellationReason = :reason " +
            "WHERE j.id = :id AND j.status IN :fromStatuses"
    )
    int transitionToCancelled(
        @Param("id") UUID id,
        @Param("now") Instant now,
        @Param("error") String error,
        @Param("reason") AgentJobCancellationReason reason,
        @Param("fromStatuses") Collection<AgentJobStatus> fromStatuses
    );

    /**
     * Like {@link #transitionToCancelled}, fenced to the owning worker (#1368 fix wave, mirrors
     * {@link #transitionStatusOwnedBy}): a worker draining a job it believes it still owns must not
     * cancel a sibling's run if the job was orphan-requeued out from under it between the drain
     * snapshot and this write.
     *
     * @return number of rows updated (0 or 1)
     */
    @WorkspaceAgnostic("ID-based fenced cancel; job ID + owner from worker-local drain context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus.CANCELLED, " +
            "j.completedAt = :now, j.errorMessage = :error, j.cancellationReason = :reason " +
            "WHERE j.id = :id AND j.status IN :fromStatuses AND j.workerId = :workerId"
    )
    int transitionToCancelledOwnedBy(
        @Param("id") UUID id,
        @Param("now") Instant now,
        @Param("error") String error,
        @Param("reason") AgentJobCancellationReason reason,
        @Param("fromStatuses") Collection<AgentJobStatus> fromStatuses,
        @Param("workerId") String workerId
    );

    /**
     * Persist the run-provenance digests. Written before the sandbox starts, so even a failed or cancelled run
     * keeps the record of what it was about to consume.
     */
    @WorkspaceAgnostic("ID-based provenance stamp; job ID from worker-local execution context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentJob j SET j.promptDigest = :promptDigest, j.inputsDigest = :inputsDigest WHERE j.id = :id")
    int updateProvenanceDigests(
        @Param("id") UUID id,
        @Param("promptDigest") String promptDigest,
        @Param("inputsDigest") String inputsDigest
    );

    /**
     * Poll-loop candidate lookup (#1368 NATS→Postgres cutover): the oldest {@code QUEUED} job ids, up
     * to this worker's free local capacity. A light id-only projection — {@link
     * #findByIdQueuedForUpdateSkipLocked} re-checks and locks each candidate individually, so a stale
     * read here (a job already claimed by a sibling between this query and the claim attempt) just
     * costs a skipped candidate, never a double-claim.
     *
     * <p>Fairness predicate (#1368 fix wave): excludes candidates whose {@code agent_config} is
     * already at its {@code max_concurrent_jobs} cap. Without this, a config with a deep QUEUED
     * backlog that is fully saturated on RUNNING jobs monopolises every LIMIT window — {@code
     * processJob} would re-check and skip every one of them (correctly refusing to over-claim), but a
     * younger, immediately-runnable job from a different config never even gets fetched into the
     * candidate batch, so it waits out poll cycle after poll cycle behind jobs nobody can claim yet
     * (head-of-line starvation). The concurrency count re-check inside {@code claimJob}'s {@code
     * FOR UPDATE} transaction remains the authoritative gate — this predicate only shapes which
     * candidates are worth fetching in the first place, so a stale read here (a RUNNING job
     * completing between this query and the claim) costs nothing beyond an ordinary skipped
     * candidate. Backed by {@code ix_agent_job_queued_created} (queued jobs, oldest-first) and {@code
     * ix_agent_job_running_config} (running jobs per config, for the correlated count).
     */
    @WorkspaceAgnostic("Cross-workspace poll candidates; caller is the @WorkspaceAgnostic job poller")
    @Query(
        value = "SELECT j.id FROM agent_job j " +
            "WHERE j.status = 'QUEUED' " +
            "AND (" +
            "  j.config_id IS NULL " +
            "  OR (SELECT count(*) FROM agent_job r WHERE r.config_id = j.config_id AND r.status = 'RUNNING') " +
            "     < (SELECT c.max_concurrent_jobs FROM agent_config c WHERE c.id = j.config_id)" +
            ") " +
            "ORDER BY j.created_at ASC LIMIT :limit",
        nativeQuery = true
    )
    List<UUID> findQueuedIdsOldestFirst(@Param("limit") int limit);

    /** Stale RUNNING reaper: find RUNNING jobs that exceeded their expected lifetime. */
    @WorkspaceAgnostic("Cross-workspace stale job reaper; caller is @WorkspaceAgnostic sweeper")
    @Query("SELECT j FROM AgentJob j WHERE j.status = 'RUNNING' AND j.startedAt < :cutoff")
    List<AgentJob> findStaleRunningJobs(@Param("cutoff") Instant cutoff);

    /**
     * Orphan recovery (#1138): RUNNING jobs whose owning worker has no fresh heartbeat (crashed /
     * partitioned / gone). Native so the liveness comparison stays on the DB clock both sides —
     * {@code last_heartbeat} is written with the DB {@code now()}, and the cutoff is
     * {@code now() - leaseTtlSeconds}, so no app/DB skew enters the alive/dead decision. The
     * {@code startedAt < :graceCutoff} startup grace is app-clock on both sides (started_at is set
     * app-side at claim); clock skew there only shifts grace timing and cannot cause a false orphan.
     */
    @WorkspaceAgnostic("Cross-workspace orphan recovery; caller is @WorkspaceAgnostic sweeper")
    @Query(
        value = "SELECT j.id AS jobId, j.workspace_id AS workspaceId, j.retry_count AS retryCount, " +
            "j.worker_id AS workerId " +
            "FROM agent_job j WHERE j.status = 'RUNNING' AND j.worker_id IS NOT NULL " +
            "AND j.started_at < :graceCutoff " +
            "AND NOT EXISTS (SELECT 1 FROM worker_registry w WHERE w.worker_id = j.worker_id " +
            "AND w.last_heartbeat >= now() - make_interval(secs => :leaseTtlSeconds))",
        nativeQuery = true
    )
    List<OrphanedJobRef> findOrphanedRunningJobs(
        @Param("graceCutoff") Instant graceCutoff,
        @Param("leaseTtlSeconds") long leaseTtlSeconds
    );

    /**
     * CAS requeue of an orphaned (or draining) job: RUNNING → QUEUED, clear ownership, bump
     * retry_count. Returns 1 if this caller won the race, 0 otherwise.
     *
     * <p>Fenced on {@code worker_id = :workerId} (#1368 fix wave): checking status alone let a
     * belated requeue attempt (e.g. a slow sweeper pass, or a second replica's concurrent sweep)
     * match a row that a DIFFERENT worker has since legitimately re-claimed — status is RUNNING
     * again, just under a new owner — silently stealing that worker's in-progress job back to
     * QUEUED. Fencing on the worker id this caller believes it is recovering from closes that: the
     * UPDATE only matches while the row is still owned by the worker the caller identified as
     * dead/draining. Also enforces the retry cap in the WHERE clause (defense in depth — callers
     * already check {@code retryCount < maxRetries} before calling, but a future caller that forgets
     * to must not be able to requeue past the cap).
     *
     * <p>Reused by two callers: {@link AgentJobZombieSweeper#recoverOrphanedJobs} (the dead worker's
     * own id) and {@link AgentJobExecutor#cancelInFlight} on drain timeout (this worker's own id,
     * requeuing its own in-flight jobs for a sibling to pick up rather than cancelling them —
     * matches the documented drain contract).
     */
    @WorkspaceAgnostic("ID-based orphan/drain requeue; caller is @WorkspaceAgnostic sweeper or worker-local drain")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, " +
            "j.startedAt = null, j.retryCount = j.retryCount + 1 " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId AND j.retryCount < :maxRetries"
    )
    int requeueOrphan(@Param("id") UUID id, @Param("workerId") String workerId, @Param("maxRetries") int maxRetries);

    /**
     * Self-fenced requeue for a claim this SAME worker just won but could not dispatch (sandbox
     * executor pool rejection, #1368 fix wave): RUNNING → QUEUED, ownership cleared, {@code
     * retry_count} left untouched. The job never actually started executing — refusing it before
     * dispatch is a delivery-mechanism hiccup, not a failed attempt, so it must not burn part of the
     * job's retry budget (that would let a chronically undersized sandbox pool exhaust {@code
     * max-retries} on jobs that never ran). Always fenced to the worker id that just claimed it
     * (this call happens synchronously, moments after the claim, on the same poll thread), so no
     * orphan risk from fencing here — {@code :workerId} may be {@code null} only when the worker
     * role runs with no identity configured, in which case the check is skipped.
     *
     * @return 1 if requeued, 0 if the row is no longer RUNNING-and-ours (should not normally happen
     *     given the timing, but the caller treats 0 the same as 1 — either way the claim is gone)
     */
    @WorkspaceAgnostic("ID-based self-fenced requeue; caller is the claiming worker itself")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, j.startedAt = null " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND (:workerId IS NULL OR j.workerId = :workerId)"
    )
    int requeueRejectedClaim(@Param("id") UUID id, @Param("workerId") String workerId);

    // Delivery tracking (issue #748)

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
}

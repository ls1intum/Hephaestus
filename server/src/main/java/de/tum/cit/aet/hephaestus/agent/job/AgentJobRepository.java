package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentJobRepository extends JpaRepository<AgentJob, UUID> {
    Page<AgentJob> findByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<AgentJob> findByWorkspaceIdAndStatus(Long workspaceId, AgentJobStatus status, Pageable pageable);

    Optional<AgentJob> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    long countByWorkspaceIdAndPurposeAndStatusIn(
        Long workspaceId,
        AgentPurpose purpose,
        Collection<AgentJobStatus> statuses
    );

    List<AgentJob> findByStatus(AgentJobStatus status);

    List<AgentJob> findByStatusIn(Collection<AgentJobStatus> statuses);

    Optional<AgentJob> findByJobTokenHashAndStatus(String jobTokenHash, AgentJobStatus status);

    /**
     * Clears the hour-long hold a budget block placed on this workspace's queued jobs, so raising the
     * cap takes effect immediately. Scoped to {@code hold_reason = 'BUDGET'} rather than "any future
     * {@code available_at}" so it cannot fast-forward a crash-retry backoff.
     *
     * @return how many held jobs were released
     */
    @WorkspaceAgnostic("Workspace-scoped release; caller is the budget writer for that workspace")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.availableAt = :now, j.holdReason = null " +
            "WHERE j.workspace.id = :workspaceId AND j.status = 'QUEUED' AND j.holdReason = 'BUDGET'"
    )
    int releaseBudgetHolds(@Param("workspaceId") Long workspaceId, @Param("now") Instant now);

    Optional<AgentJob> findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
        Long workspaceId,
        String idempotencyKey,
        Collection<AgentJobStatus> statuses
    );

    /** The key prefix is PR- and config-scoped but SHA-agnostic, so this matches re-reviews of the same PR. */
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
     * Empty if a concurrent poller holds the row, or it is no longer eligible. Eligibility is
     * re-checked here and not only in the candidate poll, because a concurrent backoff-requeue can push
     * {@code available_at} into the future in between. {@code :now} is bound rather than read from the
     * DB clock so eligibility is judged against the same app clock that computed {@code available_at}.
     */
    @WorkspaceAgnostic("ID-based claim; job ID from a workspace-scoped candidate poll")
    @Query(
        value = "SELECT * FROM agent_job WHERE id = :id AND status = 'QUEUED' AND available_at <= :now " +
            "FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<AgentJob> findByIdQueuedForUpdateSkipLocked(@Param("id") UUID id, @Param("now") Instant now);

    @WorkspaceAgnostic("ID-based reload; job ID from workspace-scoped claim context")
    @Query("SELECT j FROM AgentJob j LEFT JOIN FETCH j.workspace WHERE j.id = :id")
    Optional<AgentJob> findByIdWithWorkspace(@Param("id") UUID id);

    /**
     * The row lock makes this read and the caller's following status transition one decision: a
     * concurrent execution-start CAS either commits before this read or waits and then loses because
     * the job is no longer RUNNING.
     */
    @WorkspaceAgnostic("ID-based locked recovery read; caller performs a fenced status transition")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM AgentJob j LEFT JOIN FETCH j.workspace WHERE j.id = :id")
    Optional<AgentJob> findByIdWithWorkspaceForUpdate(@Param("id") UUID id);

    /** @return rows updated (0 or 1); 0 means a concurrent transition won. */
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
     * Like {@link #transitionStatus}, but a worker whose job was orphan-requeued to a sibling cannot
     * clobber the sibling's run with its own late terminal write.
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
     * Unfenced by worker; callers that know the owning worker should prefer
     * {@link #transitionToCancelledOwnedBy}.
     *
     * @return rows updated (0 or 1)
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
     * Adds one proxied call's tokens to the running totals of ONE attempt, so a job that crashes
     * mid-run still has the calls it made on record. A clean finish overwrites these with the runner's
     * authoritative totals.
     *
     * <p>Fenced on {@code retry_count} and {@code RUNNING} because a provider call can outlive the
     * attempt that issued it: {@link #requeueOrphan} may hand the row to a new attempt (zeroing these
     * columns) while the proxy is still waiting on the provider, and a late write would otherwise bill
     * one attempt's tokens at another attempt's frozen price and funding source.
     *
     * <p>{@code llm_cache_write_tokens} is not touched: the OpenAI-compatible usage shapes the proxy
     * parses do not report cache writes, so only the runner's end-of-run report fills that column.
     *
     * @param attempt the {@code retry_count} the caller observed when it authenticated the call
     * @return 1 if the attempt still owns the row, 0 if it has been superseded (a safe no-op)
     */
    @WorkspaceAgnostic("ID-based per-call usage accumulation from the worker-local proxy")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET " +
            "j.llmTotalCalls = COALESCE(j.llmTotalCalls, 0) + 1, " +
            "j.llmTotalInputTokens = COALESCE(j.llmTotalInputTokens, 0) + :input, " +
            "j.llmTotalOutputTokens = COALESCE(j.llmTotalOutputTokens, 0) + :output, " +
            "j.llmTotalReasoningTokens = COALESCE(j.llmTotalReasoningTokens, 0) + :reasoning, " +
            "j.llmCacheReadTokens = COALESCE(j.llmCacheReadTokens, 0) + :cacheRead " +
            "WHERE j.id = :id AND j.retryCount = :attempt AND j.status = 'RUNNING'"
    )
    int accumulateLlmUsage(
        @Param("id") UUID id,
        @Param("attempt") int attempt,
        @Param("input") int input,
        @Param("output") int output,
        @Param("reasoning") int reasoning,
        @Param("cacheRead") int cacheRead
    );

    /**
     * Reads the totals straight from the row rather than from a possibly stale in-memory entity, so
     * committed proxy accumulations are included.
     */
    @WorkspaceAgnostic("ID-based usage read; job ID from worker-local terminal accounting")
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.agent.job.AgentJobLlmUsage(" +
            "COALESCE(j.llmTotalCalls, 0), COALESCE(j.llmTotalInputTokens, 0), " +
            "COALESCE(j.llmTotalOutputTokens, 0), COALESCE(j.llmTotalReasoningTokens, 0), " +
            "COALESCE(j.llmCacheReadTokens, 0), COALESCE(j.llmCacheWriteTokens, 0)) " +
            "FROM AgentJob j WHERE j.id = :id"
    )
    Optional<AgentJobLlmUsage> findLlmUsageById(@Param("id") UUID id);

    /**
     * Like {@link #transitionToCancelled}, fenced to the owning worker: a draining worker must not
     * cancel a sibling's run if the job was orphan-requeued out from under it.
     *
     * @return rows updated (0 or 1)
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

    /** Persists the accounting boundary immediately before sandbox/provider execution. */
    @WorkspaceAgnostic("ID-based execution-start fence; job ID + owner from worker-local execution context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.executionStartedAt = :now " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND j.executionStartedAt IS NULL " +
            "AND ((:workerId IS NULL AND j.workerId IS NULL) OR j.workerId = :workerId)"
    )
    int markExecutionStarted(@Param("id") UUID id, @Param("workerId") String workerId, @Param("now") Instant now);

    /** Written before the sandbox starts, so a failed or cancelled run still records what it consumed. */
    @WorkspaceAgnostic("ID-based provenance stamp; job ID from worker-local execution context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentJob j SET j.promptDigest = :promptDigest, j.inputsDigest = :inputsDigest WHERE j.id = :id")
    int updateProvenanceDigests(
        @Param("id") UUID id,
        @Param("promptDigest") String promptDigest,
        @Param("inputsDigest") String inputsDigest
    );

    /**
     * Poll-loop candidates, id-only because {@link #findByIdQueuedForUpdateSkipLocked} re-checks and
     * locks each one; a stale read here costs at most a skipped candidate.
     *
     * <p>Candidates whose {@code (workspace, purpose)} binding is already at its
     * {@code max_concurrent_jobs} cap are excluded. Without that, one saturated workspace-purpose with
     * a deep backlog fills every LIMIT window with jobs nobody can claim, and a younger runnable job
     * elsewhere never reaches the batch. A candidate with no binding row is still fetched — the claim's
     * admission re-check is the authoritative gate.
     */
    @WorkspaceAgnostic("Cross-workspace poll candidates; caller is the @WorkspaceAgnostic job poller")
    @Query(
        value = "SELECT j.id FROM agent_job j " +
            "WHERE j.status = 'QUEUED' " +
            "AND j.available_at <= now() " +
            "AND (" +
            "  (SELECT count(*) FROM agent_job r " +
            "     WHERE r.workspace_id = j.workspace_id AND r.purpose = j.purpose AND r.status = 'RUNNING') " +
            "  < COALESCE((SELECT b.max_concurrent_jobs FROM workspace_agent_binding b " +
            "     WHERE b.workspace_id = j.workspace_id AND b.purpose = j.purpose), 2147483647)" +
            ") " +
            "ORDER BY j.available_at ASC, j.id ASC LIMIT :limit",
        nativeQuery = true
    )
    List<UUID> findQueuedIdsOldestFirst(@Param("limit") int limit);

    @WorkspaceAgnostic("Cross-workspace stale job reaper; caller is @WorkspaceAgnostic sweeper")
    @Query("SELECT j FROM AgentJob j WHERE j.status = 'RUNNING' AND j.startedAt < :cutoff")
    List<AgentJob> findStaleRunningJobs(@Param("cutoff") Instant cutoff);

    /**
     * RUNNING jobs whose owning worker has no fresh heartbeat. Native so the liveness comparison stays
     * on the DB clock on both sides ({@code last_heartbeat} is written with the DB {@code now()}), and
     * no app/DB skew can produce a false orphan.
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
     * CAS requeue of an orphaned or draining job: RUNNING → QUEUED, ownership cleared, retry_count
     * bumped.
     *
     * <p>Fenced on {@code worker_id}: status alone would let a belated requeue steal a job that a
     * different worker has since legitimately re-claimed. The retry cap is in the WHERE clause too, so
     * a caller that forgets to check it cannot requeue past the cap.
     *
     * <p>Rotates the job token so a merely partitioned zombie sandbox cannot keep authenticating
     * against the LLM proxy once a sibling re-claims the row, and zeroes the per-attempt LLM
     * accumulators: the caller bills the dead attempt before requeuing, so leaving the totals would
     * make the next attempt's terminal billing record the overlap a second time.
     *
     * @param availableAt when the requeued job becomes claimable again (now + backoff, so a
     *     crash-looping job cannot burn its whole retry budget in seconds)
     * @param newJobToken freshly generated plaintext token (encrypted at rest by the entity's converter)
     * @param newJobTokenHash {@code AgentJob.computeTokenHash(newJobToken)} — indexed lookup hash
     * @return 1 if this caller won the race, 0 otherwise
     */
    @WorkspaceAgnostic("ID-based orphan/drain requeue; caller is @WorkspaceAgnostic sweeper or worker-local drain")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, " +
            "j.startedAt = null, j.executionStartedAt = null, " +
            "j.retryCount = j.retryCount + 1, j.availableAt = :availableAt, " +
            "j.jobToken = :newJobToken, j.jobTokenHash = :newJobTokenHash, " +
            "j.llmTotalCalls = 0, j.llmTotalInputTokens = 0, j.llmTotalOutputTokens = 0, " +
            "j.llmTotalReasoningTokens = 0, j.llmCacheReadTokens = 0, j.llmCacheWriteTokens = 0 " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId AND j.retryCount < :maxRetries"
    )
    int requeueOrphan(
        @Param("id") UUID id,
        @Param("workerId") String workerId,
        @Param("maxRetries") int maxRetries,
        @Param("availableAt") Instant availableAt,
        @Param("newJobToken") String newJobToken,
        @Param("newJobTokenHash") String newJobTokenHash
    );

    /**
     * Requeue of a claim this same worker just won but could not dispatch (sandbox executor pool
     * rejection). The job never started, so {@code retry_count} is left untouched — otherwise an
     * undersized sandbox pool would exhaust {@code max-retries} on jobs that never ran. {@code
     * available_at} is left untouched too: the row could only have been claimed because it was already
     * in the past, so it stays immediately reclaimable. No token rotation — no sandbox ever saw it.
     * {@code :workerId} is null only when the worker role runs with no identity configured.
     *
     * @return 1 if requeued, 0 if the row is no longer RUNNING-and-ours; the caller treats both the
     *     same, since either way the claim is gone
     */
    @WorkspaceAgnostic("ID-based self-fenced requeue; caller is the claiming worker itself")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, j.startedAt = null, " +
            "j.executionStartedAt = null " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND (:workerId IS NULL OR j.workerId = :workerId)"
    )
    int requeueRejectedClaim(@Param("id") UUID id, @Param("workerId") String workerId);

    @WorkspaceAgnostic("ID-based delivery update; job ID from workspace-scoped delivery context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentJob j SET j.deliveryStatus = :status, j.deliveryCommentId = :commentId " + "WHERE j.id = :id")
    void updateDeliveryStatus(
        @Param("id") UUID id,
        @Param("status") DeliveryStatus status,
        @Param("commentId") String commentId
    );

    /** @return 1 if transitioned, 0 if the current status matched none of {@code fromStatuses}. */
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

    /** Bounded by {@code pageable} so one sweep pass never loads an unbounded backlog. */
    @WorkspaceAgnostic("Cross-workspace delivery-recovery sweep; caller is @WorkspaceAgnostic sweeper")
    @Query(
        "SELECT j FROM AgentJob j WHERE j.status = 'COMPLETED' AND j.deliveryStatus = 'PENDING' " +
            "AND j.completedAt < :cutoff ORDER BY j.completedAt ASC"
    )
    List<AgentJob> findStuckPendingDeliveries(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Increments {@code delivery_attempts} only if it still matches {@code expectedAttempts}, so two
     * concurrent sweeper passes cannot both re-post the same stuck delivery. The winner (1) proceeds
     * with the redelivery; a loser (0) skips this pass.
     */
    @WorkspaceAgnostic("ID-based delivery-recovery CAS; job ID from workspace-scoped sweep candidate")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.deliveryAttempts = j.deliveryAttempts + 1 " +
            "WHERE j.id = :id AND j.status = 'COMPLETED' AND j.deliveryStatus = 'PENDING' " +
            "AND j.deliveryAttempts = :expectedAttempts"
    )
    int claimDeliveryRecoveryAttempt(@Param("id") UUID id, @Param("expectedAttempts") short expectedAttempts);

    /**
     * Terminal write for a delivery-recovery attempt, fenced on the {@code delivery_attempts} value the
     * caller's own {@link #claimDeliveryRecoveryAttempt} claimed. That counter is not a lease, so a slow
     * attempt spanning several sweep passes can be superseded by a later one; without the fence,
     * whichever finished last would win and a stale FAILED could clobber a DELIVERED.
     *
     * @return 1 if this attempt's result was recorded; 0 if a later attempt superseded it — treat as
     *     lost and do not retry the write
     */
    @WorkspaceAgnostic("ID-based fenced delivery-recovery terminal write; job ID from workspace-scoped sweep candidate")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.deliveryStatus = :newStatus, j.deliveryCommentId = :commentId " +
            "WHERE j.id = :id AND j.status = 'COMPLETED' AND j.deliveryStatus IN :fromStatuses " +
            "AND j.deliveryAttempts = :expectedAttempts"
    )
    int transitionDeliveryStatusFenced(
        @Param("id") UUID id,
        @Param("newStatus") DeliveryStatus newStatus,
        @Param("commentId") @Nullable String commentId,
        @Param("fromStatuses") Collection<DeliveryStatus> fromStatuses,
        @Param("expectedAttempts") short expectedAttempts
    );

    /**
     * Strips the heavy payload columns from terminal rows, batched so a large backlog is worked off in
     * many short transactions. Idempotent.
     *
     * <p>Excludes {@code delivery_status = 'PENDING'}: the delivery-recovery retry reads
     * {@code output}, so stripping it first would make a stuck delivery permanently undeliverable.
     */
    @WorkspaceAgnostic("Cross-workspace retention batch; caller is @WorkspaceAgnostic retention service")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "UPDATE agent_job SET container_logs = NULL, output = NULL " +
            "WHERE id IN (" +
            "  SELECT id FROM agent_job " +
            "  WHERE status IN ('COMPLETED','FAILED','TIMED_OUT','CANCELLED') " +
            "  AND completed_at < :cutoff " +
            "  AND delivery_status <> 'PENDING' " +
            "  AND (container_logs IS NOT NULL OR output IS NOT NULL) " +
            "  LIMIT :batchSize" +
            ")",
        nativeQuery = true
    )
    int stripTerminalPayloads(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    /**
     * Deletes terminal rows outright, batched like {@link #stripTerminalPayloads}.
     *
     * <p>Excludes {@code delivery_status = 'PENDING'} (deleting would drop the pending delivery
     * forever) and rows referenced by {@code feedback} ({@code feedback.agent_job_id} is
     * {@code ON DELETE RESTRICT}, and the append-only research data hanging off it must outlive the
     * operational row). Referenced rows already shed their payload columns at the earlier strip pass,
     * so keeping them is bounded growth.
     */
    @WorkspaceAgnostic("Cross-workspace retention batch; caller is @WorkspaceAgnostic retention service")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM agent_job WHERE id IN (" +
            "  SELECT j.id FROM agent_job j " +
            "  WHERE j.status IN ('COMPLETED','FAILED','TIMED_OUT','CANCELLED') " +
            "  AND j.completed_at < :cutoff " +
            "  AND j.delivery_status <> 'PENDING' " +
            "  AND NOT EXISTS (SELECT 1 FROM feedback f WHERE f.agent_job_id = j.id) " +
            "  LIMIT :batchSize" +
            ")",
        nativeQuery = true
    )
    int deleteTerminalRowsOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    /**
     * Depth, oldest-eligible-age and running count in a single pass, so the scan cost does not triple
     * exactly when an incident has inflated the backlog. {@code :now} is bound rather than read from
     * the DB clock, matching {@link #findByIdQueuedForUpdateSkipLocked}.
     */
    @WorkspaceAgnostic("Fleet-wide queue-health snapshot; caller is @WorkspaceAgnostic health sampler")
    @Query(
        value = "SELECT " +
            "  COUNT(*) FILTER (WHERE status = 'QUEUED' AND available_at <= :now) AS depth, " +
            "  MIN(available_at) FILTER (WHERE status = 'QUEUED' AND available_at <= :now) AS oldestAvailableAt, " +
            "  COUNT(*) FILTER (WHERE status = 'RUNNING') AS running " +
            "FROM agent_job WHERE status IN ('QUEUED', 'RUNNING')",
        nativeQuery = true
    )
    QueueHealthSnapshot queueHealthSnapshot(@Param("now") Instant now);

    /** {@code oldestAvailableAt} is null when the queue is empty. */
    interface QueueHealthSnapshot {
        long getDepth();

        @Nullable
        Instant getOldestAvailableAt();

        long getRunning();
    }
}

package de.tum.cit.aet.hephaestus.agent.job;

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
     * Returns empty if the row is already locked (a concurrent poller claimed it first), not QUEUED,
     * or not yet eligible ({@code available_at > :now} — #1368 fix wave).
     *
     * <p>{@code available_at <= :now} is re-checked here (not just in {@link #findQueuedIdsOldestFirst}'s
     * candidate poll): the candidate list is fetched once per poll iteration, but this claim can run
     * moments later — if a concurrent backoff-requeue (orphan recovery, drain, infra-retry) pushed this
     * exact job's {@code available_at} into the future in that window, claiming it anyway would bypass
     * the backoff that requeue just computed. {@code :now} is a bind parameter rather than the DB's
     * {@code now()} so eligibility is judged on the SAME app-clock instant {@code available_at} itself
     * was computed against (avoids app/DB clock skew — the same reasoning as {@code countEligibleQueued}
     * below).
     */
    @WorkspaceAgnostic("ID-based claim; job ID from a workspace-scoped candidate poll")
    @Query(
        value = "SELECT * FROM agent_job WHERE id = :id AND status = 'QUEUED' AND available_at <= :now " +
            "FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<AgentJob> findByIdQueuedForUpdateSkipLocked(@Param("id") UUID id, @Param("now") Instant now);

    /** Reload a job with its workspace eagerly fetched (avoids LazyInitializationException on sandbox threads). */
    @WorkspaceAgnostic("ID-based reload; job ID from workspace-scoped claim context")
    @Query("SELECT j FROM AgentJob j LEFT JOIN FETCH j.workspace WHERE j.id = :id")
    Optional<AgentJob> findByIdWithWorkspace(@Param("id") UUID id);

    /**
     * Recovery-side accounting read. The row lock makes the execution-start marker and the following
     * status transition one decision: a concurrent execution-start CAS either commits before this
     * read or waits and then loses because the job is no longer RUNNING.
     */
    @WorkspaceAgnostic("ID-based locked recovery read; caller performs a fenced status transition")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM AgentJob j LEFT JOIN FETCH j.workspace WHERE j.id = :id")
    Optional<AgentJob> findByIdWithWorkspaceForUpdate(@Param("id") UUID id);

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

    /** Persist the accounting boundary immediately before sandbox/provider execution. */
    @WorkspaceAgnostic("ID-based execution-start fence; job ID + owner from worker-local execution context")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.executionStartedAt = :now " +
            "WHERE j.id = :id AND j.status = 'RUNNING' AND j.executionStartedAt IS NULL " +
            "AND ((:workerId IS NULL AND j.workerId IS NULL) OR j.workerId = :workerId)"
    )
    int markExecutionStarted(@Param("id") UUID id, @Param("workerId") String workerId, @Param("now") Instant now);

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
     * candidate. Backed by {@code ix_agent_job_queued_available} (queued jobs eligible now, oldest
     * {@code available_at} first — #1368 hardening) and {@code ix_agent_job_running_config} (running
     * jobs per config, for the correlated count).
     *
     * <p>{@code available_at <= now()} (#1368 hardening) excludes jobs backed off after an infra
     * failure / orphan requeue / drain requeue (see {@link #requeueOrphan}) until their backoff
     * elapses — see {@link AgentJobBackoff}.
     */
    @WorkspaceAgnostic("Cross-workspace poll candidates; caller is the @WorkspaceAgnostic job poller")
    @Query(
        value = "SELECT j.id FROM agent_job j " +
            "WHERE j.status = 'QUEUED' " +
            "AND j.available_at <= now() " +
            "AND (" +
            "  j.config_id IS NULL " +
            "  OR (SELECT count(*) FROM agent_job r WHERE r.config_id = j.config_id AND r.status = 'RUNNING') " +
            "     < (SELECT c.max_concurrent_jobs FROM agent_config c WHERE c.id = j.config_id)" +
            ") " +
            "ORDER BY j.available_at ASC, j.id ASC LIMIT :limit",
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
     * <p>Reused by three callers: {@link AgentJobZombieSweeper#recoverOrphanedJobs} (the dead worker's
     * own id), {@link AgentJobExecutor#cancelInFlight} on drain timeout (this worker's own id,
     * requeuing its own in-flight jobs for a sibling to pick up rather than cancelling them —
     * matches the documented drain contract), and {@link AgentJobExecutor}'s infra-failure retry path
     * (also this worker's own id, requeuing a job that failed on a provably-infra error instead of
     * failing it terminally).
     *
     * <p>#1368 hardening: also (a) sets {@code available_at} to a backoff-computed future instant
     * (see {@link AgentJobBackoff}) instead of leaving the job instantly re-claimable — a crash-looping
     * job would otherwise burn its whole retry budget in seconds; (b) ROTATES the job token
     * ({@code job_token}/{@code job_token_hash}) — the caller mints a fresh pair (see
     * {@link AgentJob#generateJobToken}/{@link AgentJob#computeTokenHash}) so a zombie sandbox that is
     * merely network-partitioned (not actually dead) cannot keep authenticating against the LLM proxy
     * once a sibling worker re-claims this same row; the old token dies with this CAS regardless of
     * whether the zombie ever notices.
     *
     * @param availableAt when the requeued job becomes claimable again (now + backoff)
     * @param newJobToken freshly generated plaintext token (encrypted at rest by the entity's converter)
     * @param newJobTokenHash {@code AgentJob.computeTokenHash(newJobToken)} — indexed lookup hash
     */
    @WorkspaceAgnostic("ID-based orphan/drain requeue; caller is @WorkspaceAgnostic sweeper or worker-local drain")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, " +
            "j.startedAt = null, j.executionStartedAt = null, " +
            "j.retryCount = j.retryCount + 1, j.availableAt = :availableAt, " +
            "j.jobToken = :newJobToken, j.jobTokenHash = :newJobTokenHash " +
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
        // #1368 hardening: available_at is DELIBERATELY left untouched (not backed off) — the job
        // never actually started executing, so this is a delivery-mechanism hiccup, not a failed
        // attempt, and it must stay immediately reclaimable. No explicit reset to "now" is needed
        // either: this row could only have been claimed in the first place because available_at was
        // already <= now() (findQueuedIdsOldestFirst's eligibility predicate), so it is still in the
        // past moments later when this requeue runs — leaving it alone already satisfies "immediately
        // reclaimable" without a second write (and without a JPQL CURRENT_TIMESTAMP, which Hibernate 7
        // resolves to java.sql.Timestamp and refuses to assign to this Instant-typed column). No token
        // rotation either: no sandbox was ever handed this token.
        "UPDATE AgentJob j SET j.status = 'QUEUED', j.workerId = null, j.startedAt = null, " +
            "j.executionStartedAt = null " +
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

    // Delivery recovery sweep (#1368 hardening): jobs stuck at delivery_status=PENDING because the
    // executor crashed between marking PENDING (in the terminal-write transaction) and finishing
    // delivery. See AgentJobZombieSweeper#recoverStuckDeliveries.

    /**
     * Stuck PENDING deliveries older than {@code cutoff}, oldest completion first. Bounded by
     * {@code pageable} so one sweep pass never loads an unbounded backlog. Backed by
     * {@code ix_agent_job_delivery_pending}.
     */
    @WorkspaceAgnostic("Cross-workspace delivery-recovery sweep; caller is @WorkspaceAgnostic sweeper")
    @Query(
        "SELECT j FROM AgentJob j WHERE j.status = 'COMPLETED' AND j.deliveryStatus = 'PENDING' " +
            "AND j.completedAt < :cutoff ORDER BY j.completedAt ASC"
    )
    List<AgentJob> findStuckPendingDeliveries(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Single-row CAS guarding a delivery-recovery attempt: increments {@code delivery_attempts} only
     * if it still matches {@code expectedAttempts}, so two concurrent sweeper passes (different server
     * replicas) cannot both attempt (and potentially double-post) the same stuck delivery. The caller
     * that wins (returns 1) proceeds with the actual redelivery attempt; a loser (0) skips this pass.
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
     * Fenced terminal write for a delivery-recovery attempt (#1368 fix wave, finding #5):
     * {@code delivery_status} carries the CAS from {@code fromStatuses}, but the write is ALSO fenced on
     * {@code delivery_attempts = :expectedAttempts} — the value THIS attempt's own {@link
     * #claimDeliveryRecoveryAttempt} call just claimed. {@code delivery_attempts} is a counter, not a
     * lease: {@link #claimDeliveryRecoveryAttempt} guards against two callers claiming the SAME attempt
     * concurrently, but does not stop a slow attempt spanning multiple sweep passes from being
     * superseded by a later one (which claims a NEW, higher attempt number while {@code delivery_status}
     * is still {@code PENDING}). Without this fence, whichever of the two finishes LAST always wins the
     * plain {@link #updateDeliveryStatus} write — including a stale, slow FAILED clobbering an in-flight
     * or already-succeeded DELIVERED. Fencing on the attempt token this caller itself claimed means only
     * the most-recent claimant's result can ever land; a superseded attempt's final write matches no row
     * (0 rows updated) and is a safe no-op.
     *
     * @return 1 if this attempt's result was recorded; 0 if a later attempt has since superseded it
     *     (the caller should treat this the same as "lost the race" — do not retry the write)
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

    // Retention (#1368 hardening): AgentJobRetentionService. Both batched via a bounded subquery so a
    // large backlog is worked off in many short transactions instead of one long one (mirrors
    // integration.core.sync.SyncJobService's retention style — no single unbounded UPDATE/DELETE).

    /**
     * Strip heavy payload columns ({@code container_logs}, {@code output}) from up to
     * {@code batchSize} TERMINAL rows completed before {@code cutoff} that still carry a payload.
     * Idempotent — a row with both columns already NULL is not matched again.
     *
     * <p>Excludes {@code delivery_status = 'PENDING'} (#1368 fix wave, BLOCKER finding #2): a job whose
     * delivery has not landed yet may still need its {@code output} to compose the delivery-recovery
     * retry ({@code JobTypeHandler#deliver} reads {@code job.getOutput()}) — stripping it first would
     * make a stuck-PENDING job permanently undeliverable.
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
     * Delete up to {@code batchSize} TERMINAL rows completed before {@code cutoff} outright.
     *
     * <p>Two exclusions (#1368 fix wave):
     * <ul>
     *   <li><b>{@code delivery_status = 'PENDING'}</b> (finding #2) — a job whose delivery has not
     *       landed yet must survive for the delivery-recovery sweep to retry; deleting it outright
     *       would silently drop the delivery forever.</li>
     *   <li><b>Referenced by {@code feedback}</b> (finding #1, BLOCKER) — {@code feedback.agent_job_id}
     *       carries {@code ON DELETE CASCADE} (1781092589259-32: "purging a job removes its synthesized
     *       feedback"), which transitively cascades to {@code feedback_observation}, {@code
     *       feedback_placement}, and {@code reaction} — append-only research/product data that must
     *       outlive the operational {@code agent_job} row. A row referenced by {@code feedback} already
     *       shed its heavy payload columns at {@code payload-retention} (14d, well before this 90d
     *       delete), so excluding it here only means the (now-lightweight) row itself lives on — bounded,
     *       not unbounded, growth. See also 1784636803503-40, which hardens the FK itself to RESTRICT so
     *       this can never regress silently.</li>
     * </ul>
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

    // Queue health gauges (#1368 hardening): AgentQueueHealthSampler.

    /**
     * Depth + oldest-eligible-age + running count in a SINGLE pass (#1368 fix wave, finding #12):
     * previously three separate queries, each a COUNT/MIN scan — worst exactly when an incident has
     * inflated the backlog, i.e. when the signal matters most and the query is most expensive. One
     * query with {@code FILTER} clauses scans the (still index-backed, via the {@code status IN (...)}
     * predicate matching the partial indexes {@code ix_agent_job_queued_available} /
     * {@code ix_agent_job_running_config}) row set once instead of three times.
     *
     * <p>{@code :now} is a bind parameter rather than JPQL/SQL {@code now()} for the same reason as
     * elsewhere in this repository — judged on the same app-clock instant {@code available_at} was
     * computed against.
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

    /** Projection for {@link #queueHealthSnapshot}; {@code oldestAvailableAt} is null when the queue is empty. */
    interface QueueHealthSnapshot {
        long getDepth();

        @Nullable
        Instant getOldestAvailableAt();

        long getRunning();
    }
}

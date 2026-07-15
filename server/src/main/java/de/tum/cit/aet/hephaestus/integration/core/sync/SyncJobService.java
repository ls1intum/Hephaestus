package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job execution template shared by every sync trigger path (manual endpoint today; scheduled crons
 * and lifecycle triggers wrap their fan-out in this same template as a follow-up, see design doc
 * §3.4). Owns: {@link SyncJob} creation, the one-active-job-per-connection guard, the in-JVM lease
 * heartbeat, zombie reaping, outcome mapping, and retention pruning.
 *
 * <p><strong>Transaction discipline:</strong> every DB-writing step here uses an explicit
 * {@link TransactionTemplate} block rather than {@code @Transactional}. Several of these methods call
 * each other on {@code this} (e.g. {@link #run} → {@link #beginJob}/{@link #executeBody}, the progress
 * writer captured as {@code this::persistProgress}), and a self-invocation through the bean's own
 * reference bypasses the Spring AOP proxy that {@code @Transactional} relies on — silently running
 * without a transaction. {@link TransactionTemplate} has no such caveat.
 *
 * <p>The runner body itself ({@code Consumer<SyncJobHandle>}, can run for minutes to hours) is
 * deliberately NOT wrapped in any single transaction — only the bookkeeping writes around it are.
 */
@Service
public class SyncJobService {

    private static final Logger log = LoggerFactory.getLogger(SyncJobService.class);

    /** A PENDING/RUNNING job whose lease is older than this is presumed abandoned (pod restart, crash). */
    private static final Duration ABANDON_THRESHOLD = Duration.ofMinutes(15);

    /** Keep only the newest N job rows per connection (live-ops view, not an audit trail). */
    private static final int RETENTION_LIMIT = 50;

    private static final int ERROR_SUMMARY_MAX_LENGTH = 2000;

    private final SyncJobRepository syncJobRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConnectionRepository connectionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /** Live jobs currently executing in THIS JVM, keyed by job id. Per-pod on purpose (see class doc). */
    private final Map<Long, SyncJobHandle> activeHandles = new ConcurrentHashMap<>();

    public SyncJobService(
        SyncJobRepository syncJobRepository,
        WorkspaceRepository workspaceRepository,
        ConnectionRepository connectionRepository,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate
    ) {
        this.syncJobRepository = syncJobRepository;
        this.workspaceRepository = workspaceRepository;
        this.connectionRepository = connectionRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /** A created job row plus its live handle, ready for {@link #executeBody}. */
    public record Started(SyncJob job, SyncJobHandle handle) {}

    /**
     * Create the job row and register its handle, enforcing the one-active-job-per-connection
     * invariant. Runs an inline abandoned-job reap for this connection FIRST, so a prior crashed run
     * never blocks a fresh "Sync now" for the full sweep interval.
     *
     * @throws SyncJobConflictException if the connection already has a PENDING/RUNNING job — carries
     *                                   that job so the caller can answer with it (idempotent-absorb)
     *                                   instead of erroring
     */
    public Started beginJob(SyncJobRequest request) {
        long connectionId = request.connectionId();
        reapAbandonedForConnection(connectionId);

        SyncJob job;
        try {
            job = transactionTemplate.execute(status -> {
                Optional<SyncJob> active = syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                    connectionId,
                    SyncJobStatus.ACTIVE
                );
                if (active.isPresent()) {
                    throw new SyncJobConflictException(active.get());
                }
                return syncJobRepository.saveAndFlush(
                    new SyncJob(
                        workspaceRepository.getReferenceById(request.workspaceId()),
                        connectionRepository.getReferenceById(connectionId),
                        request.kind(),
                        request.type(),
                        request.trigger(),
                        request.triggeredByUserId()
                    )
                );
            });
        } catch (DataIntegrityViolationException e) {
            // Partial unique index race (ux_sync_job_active): another concurrent trigger inserted first.
            // The insert transaction above is now aborted, so the winning row must be re-read in a FRESH
            // transaction — querying the aborted one would itself throw and mask the conflict as a 500.
            SyncJob raced = transactionTemplate.execute(status ->
                syncJobRepository
                    .findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(connectionId, SyncJobStatus.ACTIVE)
                    .orElse(null)
            );
            if (raced != null) {
                throw new SyncJobConflictException(raced);
            }
            throw e;
        }

        // The handle is registered in executeBody once the job is RUNNING — not here — so a rejected
        // async dispatch (executeBody never runs) can't leak a handle that the lease heartbeat would
        // keep alive forever, wedging the connection's one-active-job slot.
        SyncJobHandle handle = new SyncJobHandle(job.getId(), this::persistProgress);
        publish(request.workspaceId(), connectionId, request.kind(), SyncStateChangedEvent.Scope.JOB);
        return new Started(job, handle);
    }

    /**
     * Run the body for an already-created job: PENDING → RUNNING, invoke, map the outcome, always
     * unregister the handle and prune retention. Callers that dispatch the body asynchronously (the
     * manual-trigger REST endpoint, on the bounded {@code applicationTaskExecutor}) call
     * {@link #beginJob} synchronously first (so a duplicate-active conflict surfaces immediately) and
     * this method afterward, off-thread.
     *
     * <p>Never rethrows the body's exception — a job runner records failure in the row rather than
     * propagating to whatever dispatched it (e.g. an async executor, which would otherwise just log an
     * uncaught-exception warning with no job-row context).
     */
    public void executeBody(Started started, Consumer<SyncJobHandle> body) {
        long jobId = started.job().getId();
        SyncJobHandle handle = started.handle();
        activeHandles.put(jobId, handle);
        // Seed the local cancel flag from the row: a cancel may have landed while the job was still
        // PENDING (before this handle was registered), setting cancel_requested in the DB only.
        handle.refreshCancellation(markRunning(jobId));
        try {
            body.accept(handle);
            SyncJobStatus finalStatus = handle.cancelledReported()
                ? SyncJobStatus.CANCELLED
                : handle.warningsReported()
                    ? SyncJobStatus.SUCCEEDED_WITH_WARNINGS
                    : SyncJobStatus.SUCCEEDED;
            completeJob(jobId, finalStatus, null, handle);
        } catch (Exception e) {
            log.warn("Sync job {} failed: {}", jobId, e.toString(), e);
            completeJob(jobId, SyncJobStatus.FAILED, truncate(summarize(e)), handle);
        } finally {
            activeHandles.remove(jobId);
            pruneRetention(started.job().getConnection().getId());
        }
    }

    /**
     * Finalize a job that was created but never dispatched (e.g. the async executor rejected the body).
     * Without this the PENDING row would hold the connection's one-active slot until the zombie sweep.
     */
    public void failStarted(Started started, String reason) {
        completeJob(started.job().getId(), SyncJobStatus.FAILED, truncate(reason), started.handle());
    }

    /**
     * Convenience for synchronous callers (unit tests; future SCHEDULED/LIFECYCLE trigger paths that
     * don't need to answer an HTTP request before the body finishes) — {@link #beginJob} then
     * {@link #executeBody} on the calling thread.
     */
    public void run(SyncJobRequest request, Consumer<SyncJobHandle> body) {
        Started started = beginJob(request);
        executeBody(started, body);
    }

    /**
     * Cooperative cancel: flips {@code cancel_requested} and, if the job is running in THIS JVM,
     * refreshes its handle immediately (no need to wait out the 60s lease-heartbeat sweep for the
     * common case where the request lands on the same pod that's executing the job).
     *
     * @throws EntityNotFoundException     if the job doesn't exist in this workspace
     * @throws SyncStateConflictException if the job is already terminal (409)
     */
    public void requestCancel(long workspaceId, long jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            SyncJob job = syncJobRepository
                .findByIdAndWorkspace_Id(jobId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("SyncJob", jobId));
            if (!SyncJobStatus.ACTIVE.contains(job.getStatus())) {
                throw new SyncStateConflictException(
                    "Cannot cancel sync job " + jobId + " — already in terminal status " + job.getStatus(),
                    Map.of("jobId", jobId, "jobStatus", job.getStatus())
                );
            }
            // Targeted flag-only UPDATE (not a full-row save): a full save would write back this stale
            // snapshot's status column and could resurrect a job that the executor just completed.
            syncJobRepository.markCancelRequested(jobId, SyncJobStatus.ACTIVE);
        });

        SyncJobHandle handle = activeHandles.get(jobId);
        if (handle != null) {
            handle.refreshCancellation(true);
        }
    }

    /**
     * Touches {@code heartbeat_at} for every job currently executing in this JVM, and refreshes each
     * handle's {@code cancelRequested} flag from the database in the same pass — the cross-pod path for
     * a cancel request landing on a different pod than the one executing the job.
     */
    @Scheduled(fixedDelay = 60, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void refreshLeases() {
        if (activeHandles.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(activeHandles.keySet());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                syncJobRepository.touchHeartbeat(ids, Instant.now());
                for (SyncJobRepository.CancelFlagProjection projection : syncJobRepository.findCancelFlags(ids)) {
                    SyncJobHandle handle = activeHandles.get(projection.getId());
                    if (handle != null) {
                        handle.refreshCancellation(projection.isCancelRequested());
                    }
                }
            });
        } catch (Exception e) {
            // Visible, not silent: a stalled heartbeat risks the zombie sweep reaping a healthy job.
            log.warn("Sync job lease heartbeat failed for {} job(s): {}", ids.size(), e.toString());
        }
    }

    /**
     * Full cross-workspace sweep, called by {@link SyncJobZombieSweeper} on startup and hourly.
     *
     * @return number of jobs reaped
     */
    public int reapAbandonedJobs() {
        Instant cutoff = Instant.now().minus(ABANDON_THRESHOLD);
        Integer reaped = transactionTemplate.execute(status ->
            reapAbandoned(syncJobRepository.findAbandoned(SyncJobStatus.ACTIVE, cutoff))
        );
        return reaped == null ? 0 : reaped;
    }

    /** Scoped inline reap run before the one-active-job guard, in its own committed transaction. */
    private void reapAbandonedForConnection(long connectionId) {
        Instant cutoff = Instant.now().minus(ABANDON_THRESHOLD);
        transactionTemplate.executeWithoutResult(status ->
            reapAbandoned(syncJobRepository.findAbandonedForConnection(connectionId, SyncJobStatus.ACTIVE, cutoff))
        );
    }

    /** Must run inside an already-open transaction (both callers wrap it in one). */
    private int reapAbandoned(List<SyncJob> stale) {
        if (stale.isEmpty()) {
            return 0;
        }
        // Status-guarded bulk write: a job whose heartbeat looked stale but which committed a terminal
        // status between the find and here is excluded by the WHERE clause, so it can't be resurrected.
        List<Long> ids = stale.stream().map(SyncJob::getId).toList();
        int reaped = syncJobRepository.markAbandoned(
            ids,
            Instant.now(),
            "Abandoned: no heartbeat (likely pod restart)",
            SyncJobStatus.ACTIVE
        );
        for (SyncJob job : stale) {
            activeHandles.remove(job.getId());
            publish(
                job.getWorkspace().getId(),
                job.getConnection().getId(),
                job.getKind(),
                SyncStateChangedEvent.Scope.JOB
            );
        }
        log.warn("Reaped {} abandoned sync job(s)", reaped);
        return reaped;
    }

    // --- bookkeeping writes (self-invoked; TransactionTemplate, never @Transactional — see class doc) ---

    /** PENDING → RUNNING; returns whether a cancel was already requested while the job was PENDING. */
    private boolean markRunning(long jobId) {
        Boolean cancelRequested = transactionTemplate.execute(status ->
            syncJobRepository
                .findById(jobId)
                .map(job -> {
                    job.setStatus(SyncJobStatus.RUNNING);
                    job.setStartedAt(Instant.now());
                    job.setHeartbeatAt(Instant.now());
                    syncJobRepository.save(job);
                    return job.isCancelRequested();
                })
                .orElse(false)
        );
        return Boolean.TRUE.equals(cancelRequested);
    }

    private void completeJob(long jobId, SyncJobStatus status, @Nullable String errorSummary, SyncJobHandle handle) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            SyncJob job = syncJobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Sync job {} vanished before completion write (status={})", jobId, status);
                return;
            }
            job.setStatus(status);
            job.setFinishedAt(Instant.now());
            job.setErrorSummary(errorSummary);
            job.setItemsProcessed(handle.currentItemsProcessed());
            job.setItemsTotal(handle.currentItemsTotal());
            if (!handle.currentProgressDetail().isEmpty()) {
                job.setProgress(handle.currentProgressDetail());
            }
            syncJobRepository.save(job);
            publish(
                job.getWorkspace().getId(),
                job.getConnection().getId(),
                job.getKind(),
                SyncStateChangedEvent.Scope.JOB
            );
        });
    }

    /** {@link SyncJobHandle.ProgressWriter} callback — the throttled DB write behind {@code handle.progress(...)}. */
    private void persistProgress(
        long jobId,
        @Nullable Integer itemsProcessed,
        @Nullable Integer itemsTotal,
        Map<String, Object> progressDetail
    ) {
        transactionTemplate.executeWithoutResult(status ->
            syncJobRepository
                .findById(jobId)
                .ifPresent(job -> {
                    job.setItemsProcessed(itemsProcessed);
                    job.setItemsTotal(itemsTotal);
                    if (!progressDetail.isEmpty()) {
                        job.setProgress(progressDetail);
                    }
                    syncJobRepository.save(job);
                    publish(
                        job.getWorkspace().getId(),
                        job.getConnection().getId(),
                        job.getKind(),
                        SyncStateChangedEvent.Scope.JOB
                    );
                })
        );
    }

    private void pruneRetention(long connectionId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                syncJobRepository.pruneOldJobs(connectionId, RETENTION_LIMIT)
            );
        } catch (Exception e) {
            log.warn("Sync job retention prune failed for connection {}: {}", connectionId, e.toString());
        }
    }

    private void publish(long workspaceId, long connectionId, IntegrationKind kind, SyncStateChangedEvent.Scope scope) {
        eventPublisher.publishEvent(new SyncStateChangedEvent(workspaceId, connectionId, kind, scope));
    }

    private static String summarize(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String truncate(String s) {
        return s.length() <= ERROR_SUMMARY_MAX_LENGTH ? s : s.substring(0, ERROR_SUMMARY_MAX_LENGTH);
    }
}

package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared sync-job execution template. Bookkeeping uses {@link TransactionTemplate} because methods
 * self-invoke; long-running runner bodies execute outside a transaction.
 */
@Service
public class SyncJobService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SyncJobService.class);

    private static final Duration ABANDON_THRESHOLD = Duration.ofMinutes(15);

    private static final int RETENTION_LIMIT = 50;

    private static final int ERROR_SUMMARY_MAX_LENGTH = 2000;

    private final SyncJobRepository syncJobRepository;
    private final ConnectionRepository connectionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    private final Map<Long, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private record ActiveExecution(SyncJobHandle handle, Thread thread) {
        void cancel() {
            handle.refreshCancellation(true);
            handle.reportCancelled();
            thread.interrupt();
        }
    }

    public SyncJobService(
        SyncJobRepository syncJobRepository,
        ConnectionRepository connectionRepository,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate
    ) {
        this.syncJobRepository = syncJobRepository;
        this.connectionRepository = connectionRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    public record Started(SyncJob job, SyncJobHandle handle) {}

    /** Creates a PENDING job after reaping a stale predecessor and enforcing the active-job guard. */
    public Started beginJob(SyncJobRequest request) {
        long connectionId = request.connectionId();
        reapAbandonedForConnection(connectionId);

        SyncJob job;
        try {
            job = transactionTemplate.execute(status -> {
                connectionRepository.acquireLifecycleLock(connectionId, request.workspaceId());
                var connection = connectionRepository
                    .findByIdAndWorkspaceId(connectionId, request.workspaceId())
                    .orElseThrow(() -> new EntityNotFoundException("Connection", connectionId));
                if (connection.getState() != IntegrationState.ACTIVE) {
                    throw new SyncStateConflictException(
                        "Cannot start sync for connection " + connectionId + " in state " + connection.getState(),
                        Map.of("connectionId", connectionId, "connectionState", connection.getState())
                    );
                }
                Optional<SyncJob> active = syncJobRepository.findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
                    connectionId,
                    SyncJobStatus.ACTIVE
                );
                if (active.isPresent()) {
                    throw new SyncJobConflictException(active.get());
                }
                SyncJob created = syncJobRepository.saveAndFlush(
                    new SyncJob(
                        connection.getWorkspace(),
                        connection,
                        connection.getKind(),
                        request.type(),
                        request.trigger(),
                        request.triggeredByUserId()
                    )
                );
                // Must be published in-transaction for the AFTER_COMMIT listener.
                publish(request.workspaceId(), connectionId, created.getKind(), SyncStateChangedEvent.Scope.JOB);
                return created;
            });
        } catch (DataIntegrityViolationException e) {
            // Re-read the partial-index race winner in a fresh transaction; the insert transaction aborted.
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

        // Register only after the body starts so a rejected dispatch cannot keep a PENDING lease alive.
        SyncJobHandle handle = new SyncJobHandle(job.getId(), this::persistProgress);
        return new Started(job, handle);
    }

    /** Runs a prepared job body and records its terminal outcome instead of propagating runner failures. */
    public void executeBody(Started started, Consumer<? super SyncJobHandle> body) {
        long jobId = started.job().getId();
        SyncJobHandle handle = started.handle();
        boolean registered = false;
        try {
            MarkRunningResult start = markRunning(jobId);
            if (!start.started()) {
                return;
            }
            ActiveExecution execution = new ActiveExecution(handle, Thread.currentThread());
            activeExecutions.put(jobId, execution);
            registered = true;
            handle.refreshCancellation(start.cancelRequested());
            if (!running.get()) {
                execution.cancel();
                completeJob(jobId, SyncJobStatus.CANCELLED, null, handle);
                return;
            }
            body.accept(handle);
            SyncJobStatus finalStatus = handle.cancelledReported()
                ? SyncJobStatus.CANCELLED
                : handle.warningsReported()
                    ? SyncJobStatus.SUCCEEDED_WITH_WARNINGS
                    : SyncJobStatus.SUCCEEDED;
            completeJob(jobId, finalStatus, null, handle);
        } catch (Exception e) {
            if (handle.cancelledReported()) {
                log.info("Sync job {} stopped during application shutdown", jobId);
                completeJob(jobId, SyncJobStatus.CANCELLED, null, handle);
                return;
            }
            log.warn("Sync job {} failed: {}", jobId, e.toString(), e);
            completeJob(jobId, SyncJobStatus.FAILED, truncate(summarize(e)), handle);
        } finally {
            if (registered) {
                activeExecutions.remove(jobId);
            }
            pruneRetention(started.job().getConnection().getId());
        }
    }

    public void failStarted(Started started, String reason) {
        completeJob(started.job().getId(), SyncJobStatus.FAILED, truncate(reason), started.handle());
    }

    public void run(SyncJobRequest request, Consumer<? super SyncJobHandle> body) {
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
            if (syncJobRepository.markCancelRequested(jobId, SyncJobStatus.ACTIVE) == 0) {
                throw new SyncStateConflictException(
                    "Cannot cancel sync job " + jobId + " because it completed concurrently",
                    Map.of("jobId", jobId)
                );
            }
            publish(workspaceId, job.getConnection().getId(), job.getKind(), SyncStateChangedEvent.Scope.JOB);
        });

        ActiveExecution execution = activeExecutions.get(jobId);
        if (execution != null) {
            execution.handle().refreshCancellation(true);
        }
    }

    /**
     * Touches {@code heartbeat_at} for every job currently executing in this JVM, and refreshes each
     * handle's {@code cancelRequested} flag from the database in the same pass — the cross-pod path for
     * a cancel request landing on a different pod than the one executing the job.
     */
    @Scheduled(fixedDelay = 60, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    @WorkspaceAgnostic("Refreshes only leases registered in this JVM; job ids are globally unique")
    public void refreshLeases() {
        if (activeExecutions.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(activeExecutions.keySet());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                syncJobRepository.touchHeartbeat(ids);
                for (SyncJobRepository.CancelFlagProjection projection : syncJobRepository.findCancelFlags(ids)) {
                    ActiveExecution execution = activeExecutions.get(projection.getId());
                    if (execution != null) {
                        execution.handle().refreshCancellation(projection.isCancelRequested());
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
        Integer reaped = transactionTemplate.execute(status ->
            reapAbandoned(syncJobRepository.findAbandoned(leaseTtlSeconds()))
        );
        return reaped == null ? 0 : reaped;
    }

    private void reapAbandonedForConnection(long connectionId) {
        transactionTemplate.executeWithoutResult(status ->
            reapAbandoned(syncJobRepository.findAbandonedForConnection(connectionId, leaseTtlSeconds()))
        );
    }

    private int reapAbandoned(List<SyncJob> stale) {
        if (stale.isEmpty()) {
            return 0;
        }
        int reaped = 0;
        for (SyncJob job : stale) {
            ActiveExecution localExecution = activeExecutions.get(job.getId());
            if (localExecution != null) {
                // The lease is stale, but this JVM still owns a live runner. Keep the row active so
                // connection teardown remains fenced until that runner actually exits; request durable
                // cancellation and interrupt it instead of manufacturing a terminal row around live work.
                syncJobRepository.markCancelRequested(job.getId(), SyncJobStatus.ACTIVE);
                localExecution.cancel();
                publish(
                    job.getWorkspace().getId(),
                    job.getConnection().getId(),
                    job.getKind(),
                    SyncStateChangedEvent.Scope.JOB
                );
                continue;
            }
            int updated = syncJobRepository.markAbandoned(
                job.getId(),
                "Abandoned: no heartbeat (likely pod restart)",
                leaseTtlSeconds()
            );
            if (updated == 0) {
                continue;
            }
            reaped++;
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

    private static long leaseTtlSeconds() {
        return ABANDON_THRESHOLD.toSeconds();
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        try {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            List<Map.Entry<Long, ActiveExecution>> executions = List.copyOf(activeExecutions.entrySet());
            if (executions.isEmpty()) {
                return;
            }
            log.info("Cancelling {} in-flight sync job(s) for application shutdown", executions.size());
            for (Map.Entry<Long, ActiveExecution> entry : executions) {
                entry.getValue().cancel();
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (Map.Entry<Long, ActiveExecution> entry : executions) {
                        syncJobRepository.markCancelRequested(entry.getKey(), SyncJobStatus.ACTIVE);
                    }
                });
            } catch (Exception e) {
                log.warn("Could not persist sync-job shutdown cancellation: {}", e.toString());
            }
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return ExecutorConfigurationSupport.DEFAULT_PHASE + 1;
    }

    private record MarkRunningResult(boolean started, boolean cancelRequested) {}

    private MarkRunningResult markRunning(long jobId) {
        MarkRunningResult result = transactionTemplate.execute(status -> {
            if (syncJobRepository.markRunning(jobId) == 0) {
                return new MarkRunningResult(false, false);
            }
            boolean cancelRequested = syncJobRepository
                .findCancelFlags(List.of(jobId))
                .stream()
                .findFirst()
                .map(SyncJobRepository.CancelFlagProjection::isCancelRequested)
                .orElse(false);
            return new MarkRunningResult(true, cancelRequested);
        });
        return result == null ? new MarkRunningResult(false, false) : result;
    }

    private void completeJob(long jobId, SyncJobStatus status, @Nullable String errorSummary, SyncJobHandle handle) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            boolean honorCancellation =
                status == SyncJobStatus.SUCCEEDED || status == SyncJobStatus.SUCCEEDED_WITH_WARNINGS;
            int updated = syncJobRepository.completeActiveJob(
                jobId,
                status,
                errorSummary,
                handle.currentItemsProcessed(),
                handle.currentItemsTotal(),
                handle.currentProgressDetail(),
                SyncJobStatus.ACTIVE,
                honorCancellation
            );
            if (updated == 0 && honorCancellation) {
                updated = syncJobRepository.completeCancelRequestedJob(
                    jobId,
                    handle.currentItemsProcessed(),
                    handle.currentItemsTotal(),
                    handle.currentProgressDetail(),
                    SyncJobStatus.ACTIVE
                );
            }
            if (updated == 0) {
                log.warn("Sync job {} was no longer active at completion (attempted {})", jobId, status);
                return;
            }
            SyncJob job = syncJobRepository.findById(jobId).orElse(null);
            if (job == null) {
                return;
            }
            publish(
                job.getWorkspace().getId(),
                job.getConnection().getId(),
                job.getKind(),
                SyncStateChangedEvent.Scope.JOB
            );
            publish(
                job.getWorkspace().getId(),
                job.getConnection().getId(),
                job.getKind(),
                SyncStateChangedEvent.Scope.RESOURCES
            );
        });
    }

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

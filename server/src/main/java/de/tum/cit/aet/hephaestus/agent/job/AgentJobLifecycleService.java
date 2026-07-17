package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.hub.WorkerJobCancelDispatcher;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Post-submission lifecycle operations on an agent job: user-initiated cancel and delivery
 * retry. Split from {@link AgentJobService} (which owns submission) so each side stays within a
 * single responsibility — cancel needs the sandbox/worker plumbing, submission the config/gate
 * plumbing, and neither needs the other's.
 */
@Service
public class AgentJobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobLifecycleService.class);

    private static final Set<AgentJobStatus> ACTIVE_STATUSES = Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING);

    private final AgentJobRepository agentJobRepository;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final TransactionTemplate transactionTemplate;
    private final @Nullable SandboxManager sandboxManager;
    private final Optional<WorkerJobCancelDispatcher> workerJobCancelDispatcher;

    public AgentJobLifecycleService(
        AgentJobRepository agentJobRepository,
        JobTypeHandlerRegistry handlerRegistry,
        TransactionTemplate transactionTemplate,
        @Nullable SandboxManager sandboxManager,
        Optional<WorkerJobCancelDispatcher> workerJobCancelDispatcher
    ) {
        this.agentJobRepository = agentJobRepository;
        this.handlerRegistry = handlerRegistry;
        this.transactionTemplate = transactionTemplate;
        this.sandboxManager = sandboxManager;
        this.workerJobCancelDispatcher = workerJobCancelDispatcher;
    }

    /**
     * Retry delivery for a completed agent job whose delivery previously FAILED.
     *
     * <p>Atomically CASes delivery {@code FAILED → PENDING} then re-runs the handler's {@code deliver()}
     * method — the same delivery path used by {@link AgentJobExecutor} after sandbox execution. Only
     * {@code FAILED} is accepted as the CAS source: admitting {@code PENDING} would let two concurrent
     * retries both succeed (a {@code PENDING → PENDING} no-op returns {@code updated=1}).
     *
     * <p><strong>PENDING is therefore not recoverable through this API.</strong> A job stuck in
     * {@code PENDING} (executor crashed between marking PENDING and finishing delivery, or this method
     * crashed after the {@code FAILED → PENDING} CAS committed) requires operator intervention to demote
     * it back to {@code FAILED} before it can be retried here.
     *
     * @param workspaceId workspace ID
     * @param jobId       job UUID
     * @return the job after delivery attempt
     */
    public AgentJob retryDelivery(Long workspaceId, UUID jobId) {
        int updated = transactionTemplate.execute(status -> {
            agentJobRepository
                .findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));

            return agentJobRepository.transitionDeliveryStatus(
                jobId,
                DeliveryStatus.PENDING,
                Set.of(DeliveryStatus.FAILED)
            );
        });

        if (updated == 0) {
            throw new AgentJobStateConflictException(
                "Cannot retry delivery: job must be COMPLETED with delivery status FAILED"
            );
        }

        // Reload the entity fresh for delivery (avoids stale detached entity issues)
        AgentJob job = transactionTemplate.execute(status ->
            agentJobRepository
                .findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()))
        );

        // Deliver outside transaction (may call external APIs like GitHub)
        JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());
        try {
            handler.deliver(job);
            transactionTemplate.executeWithoutResult(tx ->
                agentJobRepository.updateDeliveryStatus(jobId, DeliveryStatus.DELIVERED, job.getDeliveryCommentId())
            );
            log.info("Delivery retry succeeded: jobId={}", jobId);
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(tx ->
                agentJobRepository.updateDeliveryStatus(jobId, DeliveryStatus.FAILED, job.getDeliveryCommentId())
            );
            log.warn("Delivery retry failed: jobId={}, error={}", jobId, e.getMessage(), e);
            throw new AgentJobStateConflictException("Delivery retry failed: " + e.getMessage());
        }

        return transactionTemplate.execute(status ->
            agentJobRepository
                .findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()))
        );
    }

    /**
     * Cancel an agent job.
     *
     * <p>Uses conditional UPDATE to prevent cancel/executor races. If the job was already
     * cancelled, this is idempotent. If the job is in a terminal state, throws 409.
     *
     * <p>The sandbox cancel call is best-effort within the transaction. Failures are caught
     * and logged — they do not roll back the CANCELLED status transition.
     *
     * @param workspaceId workspace ID
     * @param jobId       job UUID
     * @return the cancelled job
     */
    @Transactional
    public AgentJob cancel(Long workspaceId, UUID jobId) {
        AgentJob job = agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));

        if (job.getStatus() == AgentJobStatus.CANCELLED) {
            return job; // idempotent
        }

        if (job.getStatus().isTerminal()) {
            throw new AgentJobStateConflictException("Cannot cancel job " + jobId + " in status " + job.getStatus());
        }

        int updated = agentJobRepository.transitionStatus(
            jobId,
            AgentJobStatus.CANCELLED,
            Instant.now(),
            null,
            ACTIVE_STATUSES
        );

        if (updated == 0) {
            // Executor raced us. Reload and check.
            AgentJob raced = agentJobRepository
                .findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
            if (raced.getStatus().isTerminal()) {
                throw new AgentJobStateConflictException(
                    "Cannot cancel job " + jobId + " — executor already moved it to " + raced.getStatus()
                );
            }
            // Job was claimed (QUEUED→RUNNING) between our read and CAS. Retry once.
            updated = agentJobRepository.transitionStatus(
                jobId,
                AgentJobStatus.CANCELLED,
                Instant.now(),
                null,
                ACTIVE_STATUSES
            );
            if (updated == 0) {
                // Executor raced us twice. Reload and check terminal state.
                AgentJob racedAgain = agentJobRepository
                    .findByIdAndWorkspaceId(jobId, workspaceId)
                    .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
                if (racedAgain.getStatus() != AgentJobStatus.CANCELLED) {
                    throw new AgentJobStateConflictException(
                        "Cannot cancel job " + jobId + " — executor moved it to " + racedAgain.getStatus()
                    );
                }
                return racedAgain;
            }
        }

        // Reload to read worker_id (set at claim) for cancel routing.
        AgentJob fresh = agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));

        // Split deployment: ask the owning worker to stop its container over the WSS channel (ADR 0009).
        // No-op if that worker isn't connected here — the DB transition + backstops still finish the cancel.
        workerJobCancelDispatcher.ifPresent(d -> d.dispatch(fresh.getWorkerId(), jobId, "user-cancel"));

        // Monolith / co-located worker: stop the container in-process.
        if (sandboxManager != null) {
            try {
                sandboxManager.cancel(jobId);
            } catch (Exception e) {
                log.warn("Sandbox cancel failed for job {} (status already CANCELLED): {}", jobId, e.getMessage());
            }
        }

        return fresh;
    }
}

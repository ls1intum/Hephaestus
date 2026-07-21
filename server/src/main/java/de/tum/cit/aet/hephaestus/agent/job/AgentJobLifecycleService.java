package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

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
    private final LlmUsageRecorder usageRecorder;
    private final ObjectMapper objectMapper;

    public AgentJobLifecycleService(
        AgentJobRepository agentJobRepository,
        JobTypeHandlerRegistry handlerRegistry,
        TransactionTemplate transactionTemplate,
        @Nullable SandboxManager sandboxManager,
        Optional<WorkerJobCancelDispatcher> workerJobCancelDispatcher,
        LlmUsageRecorder usageRecorder,
        ObjectMapper objectMapper
    ) {
        this.agentJobRepository = agentJobRepository;
        this.handlerRegistry = handlerRegistry;
        this.transactionTemplate = transactionTemplate;
        this.sandboxManager = sandboxManager;
        this.workerJobCancelDispatcher = workerJobCancelDispatcher;
        this.usageRecorder = usageRecorder;
        this.objectMapper = objectMapper;
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

    /** Outcome of the transactional cancel CAS — {@code dispatchCancel} gates the post-commit routing. */
    private record CancelOutcome(AgentJob job, boolean dispatchCancel) {}

    /**
     * Cancel an agent job.
     *
     * <p>Uses conditional UPDATE to prevent cancel/executor races. If the job was already
     * cancelled, this is idempotent. If the job is in a terminal state, throws 409.
     *
     * <p>The sandbox cancel call and the LLM usage ledger write both run AFTER the status-transition
     * transaction commits (#1368 fix wave — {@link LlmUsageRecorder#recordUnverifiable} MUST be called
     * post-commit, matching {@code AgentJobExecutor}'s own cancellation handling). Sandbox-cancel
     * failures are caught and logged — they do not affect the already-committed CANCELLED status.
     *
     * @param workspaceId workspace ID
     * @param jobId       job UUID
     * @return the cancelled job
     */
    public AgentJob cancel(Long workspaceId, UUID jobId) {
        CancelOutcome outcome = transactionTemplate.execute(status -> cancelTransition(workspaceId, jobId));

        // Split deployment: ask the owning worker to stop its container over the WSS channel (ADR 0009).
        // No-op if that worker isn't connected here — the DB transition + backstops still finish the cancel.
        if (outcome.dispatchCancel()) {
            workerJobCancelDispatcher.ifPresent(d -> d.dispatch(outcome.job().getWorkerId(), jobId, "user-cancel"));

            // Monolith / co-located worker: stop the container in-process.
            if (sandboxManager != null) {
                try {
                    sandboxManager.cancel(jobId);
                } catch (Exception e) {
                    log.warn("Sandbox cancel failed for job {} (status already CANCELLED): {}", jobId, e.getMessage());
                }
            }
        }

        // #1368 fix wave: a job that reached RUNNING (worker_id set at claim) may carry real, un-costed
        // spend regardless of which caller actually won the CANCELLED transition — the executor's own
        // handleCancellation, worker-drain, or this method on a concurrent/idempotent call. Attempted
        // unconditionally (no-op for a job that never started); a duplicate write is safely swallowed
        // by the ledger's unique source_id constraint (first write wins).
        recordUnverifiableUsageIfStarted(workspaceId, outcome.job());
        return outcome.job();
    }

    /** The status-transition CAS + races, run inside {@link #transactionTemplate}. */
    private CancelOutcome cancelTransition(Long workspaceId, UUID jobId) {
        AgentJob job = agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));

        if (job.getStatus() == AgentJobStatus.CANCELLED) {
            return new CancelOutcome(job, false); // idempotent — already routed/recorded by the original caller
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
                return new CancelOutcome(racedAgain, false);
            }
        }

        // Reload to read worker_id (set at claim) for cancel routing.
        AgentJob fresh = agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
        return new CancelOutcome(fresh, true);
    }

    /**
     * Append an UNPRICED ledger row for a job this call just cancelled (or found already cancelled)
     * that had started executing — signalled by {@code worker_id} being set, which happens in the SAME
     * commit as the QUEUED→RUNNING claim transition, so it is a reliable "did this job ever run" flag
     * independent of which caller won the CANCELLED race. No-op for a job cancelled before it was ever
     * claimed. Mirrors {@code AgentJobExecutor#recordUnverifiableUsage} — see that method's doc for why
     * the sample carries zeroed token counts and {@code PricingState.UNPRICED}.
     */
    private void recordUnverifiableUsageIfStarted(Long workspaceId, AgentJob job) {
        if (job.getWorkerId() == null) {
            return;
        }
        ConfigSnapshot snap = parseSnapshotQuietly(job);
        LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.from(job.getJobType()),
            job.getId(),
            snap != null ? snap.upstreamModelId() : null,
            0L,
            0L,
            0L,
            0L,
            0L,
            0,
            snap != null ? snap.connectionScope() : null,
            snap != null ? snap.connectionId() : null,
            Instant.now()
        );
        usageRecorder.recordUnverifiable(workspaceId, sample);
        log.info("Recorded UNPRICED usage ledger entry (user-cancel): jobId={}", job.getId());
    }

    /** Best-effort {@link ConfigSnapshot} parse; a malformed/missing snapshot just yields no provenance. */
    private @Nullable ConfigSnapshot parseSnapshotQuietly(AgentJob job) {
        var snapshotNode = job.getConfigSnapshot();
        if (snapshotNode == null) {
            return null;
        }
        try {
            return ConfigSnapshot.fromJson(snapshotNode, objectMapper);
        } catch (Exception e) {
            log.warn("Could not deserialise config snapshot for usage ledger provenance: {}", e.getMessage());
            return null;
        }
    }
}

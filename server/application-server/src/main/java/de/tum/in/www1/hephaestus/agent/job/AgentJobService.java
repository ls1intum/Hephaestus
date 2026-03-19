package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentJobService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobService.class);

    private static final Set<AgentJobStatus> ACTIVE_STATUSES = Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING);

    private final AgentJobRepository agentJobRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceRepository workspaceRepository;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final @Nullable SandboxManager sandboxManager;

    public AgentJobService(
        AgentJobRepository agentJobRepository,
        AgentConfigRepository agentConfigRepository,
        WorkspaceRepository workspaceRepository,
        JobTypeHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        @Nullable SandboxManager sandboxManager
    ) {
        this.agentJobRepository = agentJobRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.workspaceRepository = workspaceRepository;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.sandboxManager = sandboxManager;
    }

    // ── Read operations ──

    @Transactional(readOnly = true)
    public Page<AgentJob> getJobs(Long workspaceId, AgentJobStatus status, Long configId, Pageable pageable) {
        if (status != null && configId != null) {
            return agentJobRepository.findByWorkspaceIdAndStatusAndConfigId(workspaceId, status, configId, pageable);
        }
        if (status != null) {
            return agentJobRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        }
        if (configId != null) {
            return agentJobRepository.findByWorkspaceIdAndConfigId(workspaceId, configId, pageable);
        }
        return agentJobRepository.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public AgentJob getJob(Long workspaceId, UUID jobId) {
        return agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
    }

    // ── Submit ──

    /**
     * Submit an agent job for the given workspace.
     *
     * <p>Always queues the job — concurrency limits are enforced at execution time by the
     * executor, not at submit time. Idempotency is enforced by a partial unique index on
     * {@code (workspace_id, idempotency_key)} for active jobs.
     *
     * @param workspaceId workspace ID
     * @param jobType     the job type
     * @param request     handler-specific submission request
     * @return the created (or existing deduplicated) job, or empty if no enabled config found
     */
    @Transactional
    public Optional<AgentJob> submit(Long workspaceId, AgentJobType jobType, JobSubmissionRequest request) {
        // 1. Find first enabled config for workspace (no lock — executor handles concurrency)
        List<AgentConfig> configs = agentConfigRepository.findByWorkspaceId(workspaceId);
        AgentConfig config = configs.stream().filter(AgentConfig::isEnabled).findFirst().orElse(null);
        if (config == null) {
            log.debug("No enabled agent config for workspace: workspaceId={}", workspaceId);
            return Optional.empty();
        }

        // 2. Handler creates submission (metadata + idempotency key)
        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        // 3. Idempotency check — application-level (partial unique index is safety net)
        Optional<AgentJob> existing = agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
            workspaceId,
            submission.idempotencyKey(),
            ACTIVE_STATUSES
        );
        if (existing.isPresent()) {
            log.info(
                "Deduplicated job submission: existingJobId={}, idempotencyKey={}",
                existing.get().getId(),
                submission.idempotencyKey()
            );
            return existing;
        }

        // 4. Build and persist job
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(jobType);
        job.setMetadata(submission.metadata());
        job.setIdempotencyKey(submission.idempotencyKey());
        job.setConfigSnapshot(ConfigSnapshot.from(config).toJson(objectMapper));

        // Copy LLM API key for direct auth modes (not stored in snapshot JSONB)
        if (config.getCredentialMode() != CredentialMode.PROXY && config.getLlmApiKey() != null) {
            job.setLlmApiKey(config.getLlmApiKey());
        }

        try {
            agentJobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException e) {
            // Partial unique index race: another concurrent submit won.
            // Cannot re-query in this transaction — PostgreSQL aborts the tx on constraint violation.
            // Throw 409; the caller can retry.
            log.info("Idempotency constraint caught concurrent duplicate: key={}", submission.idempotencyKey());
            throw new AgentJobStateConflictException("A job with this idempotency key is already active");
        }

        log.info(
            "Agent job submitted: jobId={}, jobType={}, configId={}, workspaceId={}",
            job.getId(),
            jobType,
            config.getId(),
            workspaceId
        );

        // 5. Publish event — picked up by AgentJobSubmitter after transaction commits
        eventPublisher.publishEvent(new AgentJobCreatedEvent(job.getId(), workspaceId));

        return Optional.of(job);
    }

    // ── Cancel ──

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

        boolean wasRunning = job.getStatus() == AgentJobStatus.RUNNING;

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

        // Always attempt sandbox cancel for any active job — handles the TOCTOU case where
        // job transitioned QUEUED→RUNNING between our initial read and the CAS.
        // SandboxManager.cancel() is a no-op if no container is running.
        if (sandboxManager != null) {
            try {
                sandboxManager.cancel(jobId);
            } catch (Exception e) {
                log.warn("Sandbox cancel failed for job {} (status already CANCELLED): {}", jobId, e.getMessage());
            }
        }

        // Reload to return fresh state
        return agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
    }
}

package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunner;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AgentJobService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobService.class);

    private static final Set<AgentJobStatus> ACTIVE_STATUSES = Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING);

    private final AgentJobRepository agentJobRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PullRequestRepository pullRequestRepository;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final PracticeReviewProperties reviewProperties;
    private final @Nullable SandboxManager sandboxManager;

    public AgentJobService(
        AgentJobRepository agentJobRepository,
        AgentConfigRepository agentConfigRepository,
        WorkspaceRepository workspaceRepository,
        PullRequestRepository pullRequestRepository,
        JobTypeHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate,
        PracticeReviewProperties reviewProperties,
        @Nullable SandboxManager sandboxManager
    ) {
        this.agentJobRepository = agentJobRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.workspaceRepository = workspaceRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.reviewProperties = reviewProperties;
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
     * Lookup a PR by ID, build a review submission request, and submit a job.
     * Used by the dev trigger endpoint to bypass NATS.
     *
     * @param workspaceId workspace ID
     * @param prId        the pull request's DB id
     * @return description of result (job ID or error message)
     */
    public String submitReviewForPullRequest(Long workspaceId, Long prId) {
        PullRequest pr = pullRequestRepository.findByIdWithAllForGate(prId).orElse(null);
        if (pr == null) {
            return "PR not found: " + prId;
        }
        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            return "PR missing branch info: prId=" + prId;
        }

        EventPayload.PullRequestData prData = EventPayload.PullRequestData.from(pr);
        PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
            prData,
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName()
        );

        log.info("Dev trigger: submitting review for PR {} ({})", prId, pr.getHtmlUrl());

        Optional<AgentJob> job = submit(workspaceId, AgentJobType.PULL_REQUEST_REVIEW, request);
        return job.map(j -> "Job submitted: " + j.getId()).orElse("No job created (no enabled agent config?)");
    }

    /**
     * Submit agent jobs for the given workspace — one per enabled config.
     *
     * <p>Creates a job for EACH enabled agent config, with config-scoped idempotency keys.
     * This provides redundancy: if one agent times out, others may still complete.
     * Each delivery independently posts a new summary comment + diff notes.
     *
     * <p><strong>Not {@code @Transactional}</strong>: each config gets its own transaction via
     * {@link #submitForConfig}, so a constraint-violation race on one config
     * does not abort the PostgreSQL transaction for subsequent configs.
     * Callers MUST NOT wrap calls to this method in an outer {@code @Transactional},
     * as that would cause the template to join the outer transaction, defeating isolation.
     *
     * @param workspaceId workspace ID
     * @param jobType     the job type
     * @param request     handler-specific submission request
     * @return the first created (or existing deduplicated) job, or empty if no enabled config found
     */
    public Optional<AgentJob> submit(Long workspaceId, AgentJobType jobType, JobSubmissionRequest request) {
        // 1. Find ALL enabled configs for workspace
        List<AgentConfig> enabledConfigs = agentConfigRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(AgentConfig::isEnabled)
            .toList();
        if (enabledConfigs.isEmpty()) {
            log.debug("No enabled agent config for workspace: workspaceId={}", workspaceId);
            return Optional.empty();
        }

        // 2. Handler creates submission (metadata + base idempotency key)
        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        // 3. Submit a job for EACH enabled config (each in its own transaction)
        AgentJob firstJob = null;
        for (AgentConfig config : enabledConfigs) {
            AgentJob job = submitForConfig(workspace, config, jobType, submission);
            if (job != null && firstJob == null) {
                firstJob = job;
            }
        }

        return Optional.ofNullable(firstJob);
    }

    private @Nullable AgentJob submitForConfig(
        Workspace workspace,
        AgentConfig config,
        AgentJobType jobType,
        JobSubmission submission
    ) {
        String configScopedKey = submission.idempotencyKey() + ":config:" + config.getId();

        return transactionTemplate.execute(status -> {
            // Idempotency check — application-level (partial unique index is safety net)
            Optional<AgentJob> existing = agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                workspace.getId(),
                configScopedKey,
                ACTIVE_STATUSES
            );
            if (existing.isPresent()) {
                log.info(
                    "Deduplicated job submission: existingJobId={}, idempotencyKey={}",
                    existing.get().getId(),
                    configScopedKey
                );
                return existing.get();
            }

            // Cooldown check — prevent rapid re-triggering for the same PR/config.
            // Strips the commit-SHA segment from the idempotency key to match any SHA.
            int cooldown = reviewProperties.cooldownMinutes();
            if (cooldown > 0) {
                String rawPrefix = extractCooldownKeyPrefix(submission.idempotencyKey());
                // Escape SQL LIKE wildcards in the prefix to prevent unintended pattern matching
                String escaped = rawPrefix.replace("%", "\\%").replace("_", "\\_");
                String cooldownPrefix = escaped + "%:config:" + config.getId();
                Instant cutoff = Instant.now().minus(java.time.Duration.ofMinutes(cooldown));
                Optional<AgentJob> recent = agentJobRepository.findRecentJobByKeyPrefix(
                    workspace.getId(),
                    cooldownPrefix,
                    cutoff
                );
                if (recent.isPresent()) {
                    log.info(
                        "Cooldown active: skipping submission, recentJobId={}, createdAt={}, cooldownMinutes={}, key={}",
                        recent.get().getId(),
                        recent.get().getCreatedAt(),
                        cooldown,
                        configScopedKey
                    );
                    return null;
                }
            }

            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setConfig(config);
            job.setJobType(jobType);
            job.setMetadata(submission.metadata());
            job.setIdempotencyKey(configScopedKey);
            job.setConfigSnapshot(ConfigSnapshot.from(config).toJson(objectMapper));

            // Copy LLM API key — needed for all credential modes:
            // PROXY mode: proxy controller reads it to forward to upstream provider
            // API_KEY or Claude Code OAuth mode: adapter injects it as env var into the container
            if (config.getRunner().getLlmApiKey() != null) {
                job.setLlmApiKey(config.getRunner().getLlmApiKey());
            }

            try {
                agentJobRepository.saveAndFlush(job);
            } catch (DataIntegrityViolationException e) {
                // Partial unique index race: another concurrent submit won.
                // Mark rollback so the broken Hibernate Session is properly cleaned up.
                log.info("Idempotency constraint caught concurrent duplicate: key={}", configScopedKey);
                status.setRollbackOnly();
                return null;
            }

            log.info(
                "Agent job submitted: jobId={}, jobType={}, configId={}, workspaceId={}",
                job.getId(),
                jobType,
                config.getId(),
                workspace.getId()
            );

            // Publish event — picked up by AgentJobSubmitter after transaction commits
            eventPublisher.publishEvent(new AgentJobCreatedEvent(job.getId(), workspace.getId()));

            return job;
        });
    }

    // ── Retry delivery ──

    /**
     * Retry delivery for a completed agent job whose delivery failed or was never attempted.
     *
     * <p>Validates the job is COMPLETED and delivery is FAILED or PENDING, then re-runs
     * the handler's deliver() method. This is the same delivery path used by
     * {@link AgentJobExecutor#completeJob} after sandbox execution.
     *
     * @param workspaceId workspace ID
     * @param jobId       job UUID
     * @return the job after delivery attempt
     */
    public AgentJob retryDelivery(Long workspaceId, UUID jobId) {
        // CAS: atomically transition FAILED → PENDING (prevents concurrent retry races).
        // Only FAILED is allowed as source — including PENDING would let two concurrent retries
        // both CAS successfully (PENDING→PENDING is a no-op that returns updated=1).
        int updated = transactionTemplate.execute(status -> {
            // Verify the job exists in this workspace first
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

    // ── Cooldown helpers ──

    /**
     * Extract the PR-scoped prefix from an idempotency key by stripping the commit-SHA segment.
     * Input: {@code "pr_review:owner/repo:42:abc123"} → Output: {@code "pr_review:owner/repo:42:"}
     * This allows LIKE-matching against any SHA for the same PR.
     */
    static String extractCooldownKeyPrefix(String idempotencyKey) {
        // The key format is "pr_review:{nameWithOwner}:{prNumber}:{sha}"
        // We want everything up to and including the last ':' before the SHA.
        int lastColon = idempotencyKey.lastIndexOf(':');
        if (lastColon > 0) {
            return idempotencyKey.substring(0, lastColon + 1);
        }
        return idempotencyKey;
    }
}

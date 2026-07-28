package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectClass;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentJobService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobService.class);

    private static final Set<AgentJobStatus> ACTIVE_STATUSES = Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING);

    private final AgentJobRepository agentJobRepository;
    private final WorkspaceAgentBindingRepository agentBindingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConnectionService connectionService;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PracticeReviewProperties reviewProperties;
    private final LlmBudgetService llmBudgetService;
    private final LlmModelResolver llmModelResolver;

    public AgentJobService(
        AgentJobRepository agentJobRepository,
        WorkspaceAgentBindingRepository agentBindingRepository,
        WorkspaceRepository workspaceRepository,
        ConnectionService connectionService,
        JobTypeHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        PracticeReviewProperties reviewProperties,
        LlmBudgetService llmBudgetService,
        LlmModelResolver llmModelResolver
    ) {
        this.agentJobRepository = agentJobRepository;
        this.agentBindingRepository = agentBindingRepository;
        this.workspaceRepository = workspaceRepository;
        this.connectionService = connectionService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.reviewProperties = reviewProperties;
        this.llmBudgetService = llmBudgetService;
        this.llmModelResolver = llmModelResolver;
    }

    // Read operations

    @Transactional(readOnly = true)
    public Page<AgentJob> getJobs(Long workspaceId, AgentJobStatus status, Pageable pageable) {
        if (status != null) {
            return agentJobRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        }
        return agentJobRepository.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public AgentJob getJob(Long workspaceId, UUID jobId) {
        return agentJobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentJob", jobId.toString()));
    }

    // Submit

    /**
     * Build a detached PR review submission request. Reads the PR's lazy associations, so it MUST be
     * called inside the caller's open session/transaction and the resulting request submitted OUTSIDE it
     * via {@link #submitPrepared}. Null when the branch refs needed to clone/diff are absent.
     */
    @Nullable
    PullRequestReviewSubmissionRequest buildReviewRequest(PullRequest pr, @Nullable String triggerEvent) {
        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            return null;
        }
        log.info("Dev trigger: building review request for PR {} ({})", pr.getId(), pr.getHtmlUrl());
        return new PullRequestReviewSubmissionRequest(
            ScmEventPayload.PullRequestData.from(pr),
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName(),
            triggerEvent
        );
    }

    /** Issue-shaped companion to {@link #buildReviewRequest}, with the same session requirement. */
    @Nullable
    IssueReviewSubmissionRequest buildIssueRequest(Issue issue, @Nullable String triggerEvent) {
        if (issue.getRepository() == null) {
            return null;
        }
        log.info("Dev trigger: building issue request for issue {} ({})", issue.getId(), issue.getHtmlUrl());
        return new IssueReviewSubmissionRequest(
            issue.getId(),
            issue.getNumber(),
            issue.getRepository().getId(),
            issue.getRepository().getNameWithOwner(),
            issue.getTitle(),
            issue.getBody() != null ? issue.getBody() : "",
            issue.getState() != null ? issue.getState().name() : "OPEN",
            issue.getHtmlUrl(),
            issue.getUpdatedAt(),
            triggerEvent
        );
    }

    /** Submit a prepared dev request and render the result message. Call only after the build transaction commits. */
    public String submitPrepared(Long workspaceId, AgentJobType jobType, JobSubmissionRequest request) {
        Optional<AgentJob> job = submit(workspaceId, jobType, request);
        return job
            .map(j -> "Job submitted: " + j.getId())
            .orElse(
                "No job created. Practice detection is unbound or disabled for this workspace, or its " +
                    "monthly AI budget is reached (see the workspace's AI usage report)."
            );
    }

    /**
     * Submit the workspace's practice-detection job for one reviewable artifact, if it has an enabled
     * binding and the purse funding that binding still has room.
     *
     * <p><strong>Callers MUST NOT wrap this in a transaction.</strong> {@link #submitForBinding} opens
     * its own so the idempotency-key race it absorbs rolls back only that insert; joined to an outer
     * transaction, the same race would poison the caller's whole unit of work.
     *
     * @return the created (or existing, deduplicated) job; empty when the workspace has no enabled
     *     practice-detection binding, or the cap funding it is reached
     */
    public Optional<AgentJob> submit(Long workspaceId, AgentJobType jobType, JobSubmissionRequest request) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        WorkspaceAgentBinding binding = agentBindingRepository
            .findByWorkspaceIdAndPurposeWithModels(workspaceId, AgentPurpose.PRACTICE_DETECTION)
            .filter(WorkspaceAgentBinding::isEnabled)
            .orElse(null);
        if (binding == null) {
            log.debug("No practice-detection binding to run: workspaceId={}", workspaceId);
            return Optional.empty();
        }

        // THE choke point for all sandboxed LLM work, scoped to whoever pays for THIS binding — which is
        // why the binding is resolved first: an exhausted host budget must not pause work the workspace
        // funds itself, or vice versa. Eventually consistent; uncosted in-flight jobs may overshoot.
        if (llmBudgetService.blockSubmission(workspace, jobType.name(), binding.getFundingSource())) {
            return Optional.empty();
        }

        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        return Optional.ofNullable(submitForBinding(workspace, jobType, submission));
    }

    /**
     * Submit exactly one practice-detection job — never a fan-out. The binding is re-fetched inside the
     * transaction because the discovery read in {@link #submit} runs detached.
     */
    private @Nullable AgentJob submitForBinding(Workspace workspace, AgentJobType jobType, JobSubmission submission) {
        String detectionKey = submission.idempotencyKey() + ":detection";

        return transactionTemplate.execute(status -> {
            Workspace currentWorkspace = workspaceRepository
                .findByIdForUpdate(workspace.getId())
                .orElseThrow(() -> new EntityNotFoundException("Workspace", workspace.getId().toString()));
            if (currentWorkspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
                log.debug(
                    "Skipping agent job submission for inactive workspace: workspaceId={}, status={}",
                    currentWorkspace.getId(),
                    currentWorkspace.getStatus()
                );
                return null;
            }

            WorkspaceAgentBinding binding = agentBindingRepository
                .findByWorkspaceIdAndPurpose(workspace.getId(), AgentPurpose.PRACTICE_DETECTION)
                .filter(WorkspaceAgentBinding::isEnabled)
                .orElse(null);
            if (binding == null) {
                return null; // unbound or disabled since discovery
            }

            // Application-level idempotency; the partial unique index is the safety net.
            Optional<AgentJob> existing = agentJobRepository.findByWorkspaceIdAndIdempotencyKeyAndStatusIn(
                workspace.getId(),
                detectionKey,
                ACTIVE_STATUSES
            );
            if (existing.isPresent()) {
                log.info(
                    "Deduplicated job submission: existingJobId={}, idempotencyKey={}",
                    existing.get().getId(),
                    detectionKey
                );
                return existing.get();
            }

            // Cooldown: refuse a re-trigger of the same subject at ANY freshness, not just this one.
            int cooldown = workspace.getReviewSettings().resolveCooldownMinutes(reviewProperties.cooldownMinutes());
            if (cooldown > 0) {
                String rawPrefix = extractCooldownKeyPrefix(submission.idempotencyKey());
                String escaped = rawPrefix.replace("%", "\\%").replace("_", "\\_");
                String cooldownPrefix = escaped + "%:detection";
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
                        detectionKey
                    );
                    return null;
                }
            }

            AgentJob job = new AgentJob();
            job.setWorkspace(currentWorkspace);
            job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
            job.setJobType(jobType);
            job.setSubjectClass(subjectClassFor(jobType));
            job.setMetadata(submission.metadata());
            job.setIdempotencyKey(detectionKey);
            try {
                job.setConfigSnapshot(ConfigSnapshot.from(binding, llmModelResolver).toJson(objectMapper));
            } catch (IllegalStateException unavailableModel) {
                log.warn(
                    "Skipping practice-detection binding whose model is no longer available: workspaceId={}",
                    workspace.getId()
                );
                return null;
            }
            // Resolved here rather than per-path so EVERY submission carries a delivery channel; without
            // one the job still costs LLM spend but its feedback is dropped at the poster.
            var resolvedKind = connectionService.findActiveProviderKind(workspace.getId());
            if (resolvedKind.isPresent()) {
                job.setIntegrationKind(resolvedKind.get());
            } else {
                log.warn(
                    "No active SCM connection for workspace {} — agent job will run but feedback delivery " +
                        "will fail (no integrationKind). Configure a provider connection to enable delivery. jobType={}",
                    workspace.getId(),
                    jobType
                );
            }

            // The credential is NEVER frozen onto the job: the proxy resolves it live from the snapshot's
            // catalog connection reference on every call.

            try {
                agentJobRepository.saveAndFlush(job);
            } catch (DataIntegrityViolationException e) {
                // Partial unique index race: another concurrent submit won. Mark rollback so the broken
                // Hibernate Session is cleaned up.
                log.info("Idempotency constraint caught concurrent duplicate: key={}", detectionKey);
                status.setRollbackOnly();
                return null;
            }

            log.info(
                "Agent job submitted: jobId={}, jobType={}, workspaceId={}",
                job.getId(),
                jobType,
                workspace.getId()
            );

            return job;
        });
    }

    private static SubjectClass subjectClassFor(AgentJobType jobType) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW -> SubjectClass.PULL_REQUEST;
            case ISSUE_REVIEW -> SubjectClass.ISSUE;
            case CONVERSATION_REVIEW -> SubjectClass.SLACK_MESSAGE_THREAD;
        };
    }

    /**
     * Strip the trailing freshness segment (commit SHA / updatedAt) from an idempotency key of the form
     * {@code "<type>:{nameWithOwner}:{number}:{phase}:{freshness}"}, preserving the (number, phase) scope
     * so cooldown can LIKE-match any freshness of the same subject.
     */
    static String extractCooldownKeyPrefix(String idempotencyKey) {
        int lastColon = idempotencyKey.lastIndexOf(':');
        if (lastColon > 0) {
            return idempotencyKey.substring(0, lastColon + 1);
        }
        return idempotencyKey;
    }
}

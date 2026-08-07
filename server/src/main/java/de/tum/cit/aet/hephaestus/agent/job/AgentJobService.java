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
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
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
    private final PracticeRepository practiceRepository;
    private final LlmBudgetService llmBudgetService;
    private final LlmModelResolver llmModelResolver;
    private final SignalRecorder signalRecorder;

    public AgentJobService(
        AgentJobRepository agentJobRepository,
        WorkspaceAgentBindingRepository agentBindingRepository,
        WorkspaceRepository workspaceRepository,
        ConnectionService connectionService,
        JobTypeHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        PracticeReviewProperties reviewProperties,
        PracticeRepository practiceRepository,
        LlmBudgetService llmBudgetService,
        LlmModelResolver llmModelResolver,
        SignalRecorder signalRecorder
    ) {
        this.agentJobRepository = agentJobRepository;
        this.agentBindingRepository = agentBindingRepository;
        this.workspaceRepository = workspaceRepository;
        this.connectionService = connectionService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.reviewProperties = reviewProperties;
        this.practiceRepository = practiceRepository;
        this.llmBudgetService = llmBudgetService;
        this.llmModelResolver = llmModelResolver;
        this.signalRecorder = signalRecorder;
    }

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
    PullRequestReviewSubmissionRequest buildReviewRequest(PullRequest pr, @Nullable SignalName triggerSignal) {
        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            return null;
        }
        log.info("Dev trigger: building review request for PR {} ({})", pr.getId(), pr.getHtmlUrl());
        return new PullRequestReviewSubmissionRequest(
            ScmEventPayload.PullRequestData.from(pr),
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName(),
            triggerSignal
        );
    }

    /** Issue-shaped companion to {@link #buildReviewRequest}, with the same session requirement. */
    @Nullable
    IssueReviewSubmissionRequest buildIssueRequest(Issue issue, @Nullable SignalName triggerSignal) {
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
            triggerSignal
        );
    }

    /** Submit a prepared dev request and render the result message. Call only after the build transaction commits. */
    public String submitPrepared(
        Long workspaceId,
        AgentJobType jobType,
        JobSubmissionRequest request,
        @Nullable SignalKey signalKey
    ) {
        Optional<AgentJob> job = submit(workspaceId, jobType, request, signalKey);
        return job
            .map(j -> "Job submitted: " + j.getId())
            .orElse(
                "No job created. Practice reviews are unbound or disabled for this workspace, or their " +
                    "monthly AI budget is reached (see the workspace's AI usage report)."
            );
    }

    /**
     * Submit the workspace's practice-review job for one reviewable artifact, if it has an enabled
     * binding and the purse funding that binding still has room.
     *
     * <p><strong>Callers MUST NOT wrap this in a transaction.</strong> {@link #submitForBinding} opens
     * its own so the idempotency-key race it absorbs rolls back only that insert; joined to an outer
     * transaction, the same race would poison the caller's whole unit of work.
     *
     * @param signalKey the ledger entry this submission answers, already won by the caller. Every
     *     refusal below is recorded against it, which is what allows a review refused for a reason an
     *     operator can lift to happen later instead of being lost. Null for the paths that cannot name
     *     a signal yet, which then keep the older in-flight-only deduplication.
     * @return the created (or existing, deduplicated) job; empty when the workspace has no enabled
     *     practice-review binding, or the cap funding it is reached
     */
    public Optional<AgentJob> submit(
        Long workspaceId,
        AgentJobType jobType,
        JobSubmissionRequest request,
        @Nullable SignalKey signalKey
    ) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        WorkspaceAgentBinding binding = agentBindingRepository
            .findByWorkspaceIdAndPurposeWithModels(workspaceId, AgentPurpose.PRACTICE_REVIEW)
            .filter(WorkspaceAgentBinding::isEnabled)
            .orElse(null);
        if (binding == null) {
            log.debug("No practice-review binding to run: workspaceId={}", workspaceId);
            return refuse(signalKey, SignalStateReason.BINDING_DISABLED);
        }

        // THE choke point for all sandboxed LLM work, scoped to whoever pays for THIS binding — which is
        // why the binding is resolved first: an exhausted host budget must not pause work the workspace
        // funds itself, or vice versa. Eventually consistent; uncosted in-flight jobs may overshoot.
        if (llmBudgetService.blockSubmission(workspace, jobType.name(), binding.getFundingSource())) {
            return refuse(signalKey, SignalStateReason.BUDGET_EXHAUSTED);
        }

        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        return Optional.ofNullable(submitForBinding(workspace, jobType, submission, signalKey));
    }

    /**
     * Hold a refused signal open so the reaper can re-offer it, and answer the caller with the empty
     * result the refusal implies. Wrapped in the transaction template because the callers differ in
     * whether they already have one.
     */
    private Optional<AgentJob> refuse(@Nullable SignalKey signalKey, SignalStateReason reason) {
        if (signalKey != null) {
            transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(signalKey, reason));
        }
        return Optional.empty();
    }

    /**
     * Submit exactly one practice-review job — never a fan-out. The binding is re-fetched inside the
     * transaction because the discovery read in {@link #submit} runs detached.
     */
    private @Nullable AgentJob submitForBinding(
        Workspace workspace,
        AgentJobType jobType,
        JobSubmission submission,
        @Nullable SignalKey signalKey
    ) {
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
                return refuseInTransaction(signalKey, SignalStateReason.WORKSPACE_INACTIVE);
            }
            if (!Boolean.TRUE.equals(currentWorkspace.getFeatures().getPracticesEnabled())) {
                log.debug(
                    "Skipping practice review while the workspace feature is off: workspaceId={}",
                    workspace.getId()
                );
                return refuseInTransaction(signalKey, SignalStateReason.PRACTICES_DISABLED);
            }
            if (
                !practiceRepository.existsByWorkspaceIdAndUsedInNewReviewsTrueAndArtifactKind(
                    workspace.getId(),
                    artifactKindFor(jobType)
                )
            ) {
                log.debug(
                    "Skipping practice review with no active practice for its work type: workspaceId={}, jobType={}",
                    workspace.getId(),
                    jobType
                );
                return refuseInTransaction(signalKey, SignalStateReason.NO_ACTIVE_PRACTICE);
            }

            WorkspaceAgentBinding binding = agentBindingRepository
                .findByWorkspaceIdAndPurpose(workspace.getId(), AgentPurpose.PRACTICE_REVIEW)
                .filter(WorkspaceAgentBinding::isEnabled)
                .orElse(null);
            if (binding == null) {
                // Unbound or disabled since discovery.
                return refuseInTransaction(signalKey, SignalStateReason.BINDING_DISABLED);
            }

            // A keyed submission was already deduplicated by the ledger's unique constraint, which
            // outlives the job. The status-scoped check below only ever caught a duplicate while a job
            // was still running, so a redelivery after completion re-ran the whole review; it remains
            // for the paths that cannot name a signal yet.
            if (signalKey == null) {
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
            }

            // Rate limiting, not correctness — a workspace may set it to zero. It refuses a re-trigger
            // of the same subject at ANY freshness, not just this one.
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
                    return refuseInTransaction(signalKey, SignalStateReason.COOLDOWN_ACTIVE);
                }
            }

            AgentJob job = new AgentJob();
            job.setWorkspace(currentWorkspace);
            job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
            job.setJobType(jobType);
            job.setArtifactKind(artifactKindFor(jobType));
            job.setMetadata(submission.metadata());
            job.setIdempotencyKey(detectionKey);
            try {
                job.setConfigSnapshot(ConfigSnapshot.from(binding, llmModelResolver).toJson(objectMapper));
            } catch (IllegalStateException unavailableModel) {
                log.warn(
                    "Skipping practice-review binding whose model is no longer available: workspaceId={}",
                    workspace.getId()
                );
                return refuseInTransaction(signalKey, SignalStateReason.MODEL_UNAVAILABLE);
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
                // Hibernate Session is cleaned up — which also unwinds this signal's ledger row, leaving
                // the occurrence free to be recorded again rather than consumed by a job that never was.
                log.info("Idempotency constraint caught concurrent duplicate: key={}", detectionKey);
                status.setRollbackOnly();
                return null;
            }

            if (signalKey != null) {
                signalRecorder.markTriggered(signalKey, job.getId());
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

    /** Settle a refused signal inside the submission transaction, so the two stand or fall together. */
    private @Nullable AgentJob refuseInTransaction(@Nullable SignalKey signalKey, SignalStateReason reason) {
        if (signalKey != null) {
            signalRecorder.markRefused(signalKey, reason);
        }
        return null;
    }

    /**
     * The artifact a job of this type is about. One switch: until this slice there were two, one
     * producing a {@code SubjectClass} for the job row and one a {@code WorkArtifact} for the
     * observations, which is how {@code SLACK_MESSAGE_THREAD} and {@code CONVERSATION_THREAD} came to
     * name the same thing.
     */
    static ArtifactKind artifactKindFor(AgentJobType jobType) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW -> ArtifactKinds.PULL_REQUEST;
            case ISSUE_REVIEW -> ArtifactKinds.ISSUE;
            case CONVERSATION_REVIEW -> ArtifactKinds.CONVERSATION_THREAD;
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

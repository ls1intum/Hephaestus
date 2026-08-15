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
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.Objects;
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
     *
     * <p>The origin is {@link ObservationOrigin#MANUAL} on both of these builders and not negotiable: the
     * only caller is the dev trigger, and the submission request's default — LIVE whenever a trigger
     * signal is present — would otherwise fold a hand-picked replay into the population the behavioural
     * trend line is drawn from.
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
            triggerSignal,
            ObservationOrigin.MANUAL
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
            triggerSignal,
            ObservationOrigin.MANUAL
        );
    }

    /**
     * Submit a prepared dev request and render the result message. Call only after the build
     * transaction commits.
     */
    public String submitPrepared(
        Long workspaceId,
        AgentJobType jobType,
        JobSubmissionRequest request,
        @Nullable SignalKey signalKey
    ) {
        SubmissionOutcome outcome = submitWithOutcome(workspaceId, jobType, request, signalKey);
        if (outcome.job() != null) {
            return "Job submitted: " + outcome.job().getId();
        }
        return "No job created. " + outcome.requireRefusal().describe();
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
     *     refusal is recorded against it, so a review refused for a liftable reason can happen later
     *     instead of being lost. Null for paths that cannot name a signal yet.
     * @return the created (or existing, deduplicated) job; empty when the workspace has no enabled
     *     practice-review binding, or the cap funding it is reached
     */
    public Optional<AgentJob> submit(
        Long workspaceId,
        AgentJobType jobType,
        JobSubmissionRequest request,
        @Nullable SignalKey signalKey
    ) {
        return Optional.ofNullable(submitWithOutcome(workspaceId, jobType, request, signalKey).job());
    }

    /**
     * {@link #submit} for callers that must explain themselves: the same attempt, keeping the reason
     * it stopped on instead of flattening it into an empty result.
     */
    SubmissionOutcome submitWithOutcome(
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
            return refuse(signalKey, SignalStateReason.REVIEW_MODEL_UNBOUND);
        }

        // THE choke point for all sandboxed LLM work, scoped to whoever pays for THIS binding — which is
        // why the binding is resolved first: an exhausted host budget must not pause work the workspace
        // funds itself, or vice versa. Eventually consistent; uncosted in-flight jobs may overshoot.
        if (llmBudgetService.blockSubmission(workspace, jobType.name(), binding.getFundingSource())) {
            return refuse(signalKey, SignalStateReason.BUDGET_EXHAUSTED);
        }

        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        return submitForBinding(workspace, jobType, submission, signalKey);
    }

    /**
     * Hold a refused signal open so the reaper can re-offer it, and answer the caller with the reason
     * it was refused. Wrapped in the transaction template because the callers differ in whether they
     * already have one.
     */
    private SubmissionOutcome refuse(@Nullable SignalKey signalKey, SignalStateReason reason) {
        if (signalKey != null) {
            transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(signalKey, reason));
        }
        return SubmissionOutcome.refused(reason);
    }

    /**
     * Submit exactly one practice-review job — never a fan-out. The binding is re-fetched inside the
     * transaction because the discovery read in {@link #submit} runs detached.
     */
    private SubmissionOutcome submitForBinding(
        Workspace workspace,
        AgentJobType jobType,
        JobSubmission submission,
        @Nullable SignalKey signalKey
    ) {
        String detectionKey = submission.idempotencyKey() + ":detection";

        SubmissionOutcome outcome = transactionTemplate.execute(status -> {
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
            // Resolved, not filtered in SQL: a practice that inherits its tier stores NULL, and
            // `review_tier <> 'OFF'` answers UNKNOWN for it, which would refuse review for every
            // workspace that left a practice to inherit.
            if (!hasReviewablePractice(currentWorkspace, artifactKindFor(jobType))) {
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
                return refuseInTransaction(signalKey, SignalStateReason.REVIEW_MODEL_UNBOUND);
            }

            // A keyed submission was already deduplicated by the ledger's unique constraint, which
            // outlives the job. This status-scoped fallback only catches a duplicate while a job is
            // still active, so it is reached only by paths that cannot name a signal.
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
                    return SubmissionOutcome.of(existing.get());
                }
            }

            // Rate limiting, not correctness — a workspace may set it to zero. Keyed on the prefix, so
            // it refuses a re-trigger of the same subject at any revision, not just the one submitted.
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
                return SubmissionOutcome.refused(SignalStateReason.CONCURRENT_DUPLICATE);
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

            return SubmissionOutcome.of(job);
        });
        // Every branch above returns an outcome; a null here would be a refusal with no reason.
        return Objects.requireNonNull(outcome, "submission outcome");
    }

    /** Settle a refused signal inside the submission transaction, so the two stand or fall together. */
    private SubmissionOutcome refuseInTransaction(@Nullable SignalKey signalKey, SignalStateReason reason) {
        if (signalKey != null) {
            signalRecorder.markRefused(signalKey, reason);
        }
        return SubmissionOutcome.refused(reason);
    }

    /** Whether any practice of this work type resolves to a tier that admits a review. */
    private boolean hasReviewablePractice(Workspace workspace, ArtifactKind artifactKind) {
        PracticeReviewTier workspaceDefault = WorkspaceReviewDefaults.of(workspace).defaultTier();
        return practiceRepository
            .findReviewTierRows(workspace.getId())
            .stream()
            .filter(row -> artifactKind.equals(row.getArtifactKind()))
            .anyMatch(row ->
                ReviewTierResolver.resolvePractice(row.getPracticeTier(), row.getAreaTier(), workspaceDefault)
                    .tier()
                    .admitsReview()
            );
    }

    /**
     * The artifact a job of this type is about. The single mapping for both the job row and the
     * observations filed against it — a second one drifts, and the same artifact ends up with two names.
     */
    public static ArtifactKind artifactKindFor(AgentJobType jobType) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW -> ArtifactKinds.PULL_REQUEST;
            case ISSUE_REVIEW -> ArtifactKinds.ISSUE;
            case CONVERSATION_REVIEW -> ArtifactKinds.CONVERSATION_THREAD;
            case DOCUMENT_REVIEW -> ArtifactKinds.DOCUMENT;
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

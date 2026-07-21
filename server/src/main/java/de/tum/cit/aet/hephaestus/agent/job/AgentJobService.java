package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
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
    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewableArtifactLoader artifactLoader;
    private final ConnectionService connectionService;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PracticeReviewProperties reviewProperties;
    private final LlmBudgetService llmBudgetService;
    private final LlmModelResolver llmModelResolver;

    public AgentJobService(
        AgentJobRepository agentJobRepository,
        AgentConfigRepository agentConfigRepository,
        WorkspaceRepository workspaceRepository,
        ReviewableArtifactLoader artifactLoader,
        ConnectionService connectionService,
        JobTypeHandlerRegistry handlerRegistry,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        PracticeReviewProperties reviewProperties,
        LlmBudgetService llmBudgetService,
        LlmModelResolver llmModelResolver
    ) {
        this.agentJobRepository = agentJobRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.workspaceRepository = workspaceRepository;
        this.artifactLoader = artifactLoader;
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

    // Submit

    /**
     * Build a detached PR review submission request from an already-loaded entity. Reads the PR's lazy
     * associations (repository/author via {@link ScmEventPayload.PullRequestData#from}), so it MUST be called
     * inside the caller's open session/transaction. Returns {@code null} when the branch refs needed to
     * clone/diff are absent (a merged PR retains them). Callers must then submit OUTSIDE that
     * transaction via {@link #submitPrepared}, honouring {@link #submit}'s no-outer-transaction contract.
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

    /**
     * Build a detached issue-detection submission request from an already-loaded entity. Reads the issue's
     * lazy repository, so it MUST be called inside the caller's open session/transaction. Returns
     * {@code null} when the issue has no repository. Companion to {@link #buildReviewRequest}.
     */
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
            issue.getUpdatedAt(),
            triggerEvent
        );
    }

    /**
     * Submit a prepared dev request and render the result message. Invoked by the dev-trigger controller
     * AFTER the load/gate/build transaction has committed, honouring {@link #submit}'s
     * no-outer-transaction contract.
     */
    public String submitPrepared(Long workspaceId, AgentJobType jobType, JobSubmissionRequest request) {
        Optional<AgentJob> job = submit(workspaceId, jobType, request);
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
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        // Monthly budget cap (#1368): this is THE choke point for all sandboxed LLM work —
        // webhook detection, retrospective replays, dev/bot manual triggers, conversation
        // reviews — so one check pauses them all. Eventually consistent: in-flight jobs that
        // haven't been costed yet may overshoot slightly (accepted by design).
        if (llmBudgetService.blockSubmission(workspace, jobType.name())) {
            return Optional.empty();
        }

        List<AgentConfig> configs = resolvePracticeConfigs(workspace);
        if (configs.isEmpty()) {
            log.debug("No agent config to run practice detection: workspaceId={}", workspaceId);
            return Optional.empty();
        }

        JobTypeHandler handler = handlerRegistry.getHandler(jobType);
        JobSubmission submission = handler.createSubmission(request);

        AgentJob firstJob = null;
        for (AgentConfig config : configs) {
            AgentJob job = submitForConfig(workspace, config, jobType, submission);
            if (job != null && firstJob == null) {
                firstJob = job;
            }
        }

        return Optional.ofNullable(firstJob);
    }

    /**
     * Resolve the configs to submit for practice detection. If the workspace has an explicit
     * {@code practiceConfigId} binding, return only that config when it exists and is enabled
     * (bound-but-disabled = <strong>paused, returns empty</strong>); otherwise fan out to all enabled
     * configs. The bound id is loaded via the workspace-scoped finder for tenancy safety.
     *
     * <p>Note the deliberate asymmetry with the mentor ({@code MentorChatService.resolveLlmConfig}): a
     * disabled <em>practice</em> binding PAUSES detection (it is opt-in automation — silence is the safe
     * outcome), whereas a disabled <em>mentor</em> binding FALLS BACK to the oldest enabled config (the
     * mentor must stay answerable to a user who is mid-conversation).
     */
    private List<AgentConfig> resolvePracticeConfigs(Workspace workspace) {
        Long boundConfigId = workspace.getPracticeConfigId();
        if (boundConfigId != null) {
            return agentConfigRepository
                .findByIdAndWorkspaceId(boundConfigId, workspace.getId())
                .filter(AgentConfig::isEnabled)
                .map(List::of)
                .orElseGet(List::of);
        }
        return agentConfigRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .filter(AgentConfig::isEnabled)
            .toList();
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

            // Cooldown check — prevent rapid re-triggering for the same (PR, phase)/config.
            // Strips the trailing freshness segment (commit SHA / updatedAt) to match any freshness.
            int cooldown = workspace.getReviewSettings().resolveCooldownMinutes(reviewProperties.cooldownMinutes());
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
            // Explicit subject discriminator drives downstream dispatch (e.g. DiffNotePoster
            // short-circuits when subjectClass != PULL_REQUEST).
            job.setSubjectClass(subjectClassFor(jobType));
            job.setMetadata(submission.metadata());
            job.setIdempotencyKey(configScopedKey);
            job.setConfigSnapshot(ConfigSnapshot.from(config, llmModelResolver).toJson(objectMapper));
            // The SCM kind drives delivery (which channel posts the comment/diff notes). Resolve it
            // from the workspace's active connection so EVERY path (events + dev-trigger) sets it —
            // a null integrationKind made the comment poster NPE and silently drop delivery. We log
            // loudly when it can't be resolved: the job will still run (LLM cost) but delivery will
            // fail at the poster, so surfacing it here makes the misconfiguration diagnosable up front.
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

            // The credential is NEVER frozen onto the job (#1368 slice 5 — ONE credential path,
            // resolved live by the proxy from the config snapshot's connection reference / configId on
            // every call). AgentJob.llmApiKey is retained on the entity only for backward-compatible
            // column shape; nothing writes or reads it anymore.

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

            // #1368 NATS→Postgres cutover: the QUEUED insert above IS the enqueue — AgentJobExecutor's
            // poll loop discovers it directly from the agent_job table, no publish event needed.

            return job;
        });
    }

    /**
     * The persisted {@link SubjectClass} discriminator for a job type. Exhaustive over
     * {@link AgentJobType} so a new job type must declare its subject class here (compile gate).
     */
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

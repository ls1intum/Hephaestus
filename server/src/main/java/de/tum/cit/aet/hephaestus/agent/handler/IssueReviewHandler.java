package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Handler for {@link AgentJobType#ISSUE_REVIEW} jobs — the issue counterpart of
 * {@link PullRequestReviewHandler}. Issues have NO diff: the case context is the issue body, the
 * comment thread, and the lifecycle metadata, materialised by {@code IssueContentProvider} under
 * {@code context/target/}. There is no repo mount, no diff-scope filter, and no inline diff notes.
 *
 * <p>Delivery persists findings via {@link PracticeDetectionDeliveryService} (target type ISSUE),
 * composes the student-facing feedback, and posts it as an issue comment via
 * {@link PullRequestCommentPoster#postIssueFormattedBody} (best-effort, suppressed on closed issues).
 */
public class IssueReviewHandler implements JobTypeHandler {

    private static final Logger log = LoggerFactory.getLogger(IssueReviewHandler.class);

    private final JsonMapper objectMapper;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final PracticeCatalogInjector practiceCatalogInjector;
    private final PracticeDetectionResultParser resultParser;
    private final PracticeDetectionDeliveryService deliveryService;
    private final PullRequestCommentPoster commentPoster;

    IssueReviewHandler(
        JsonMapper objectMapper,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter,
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        PullRequestCommentPoster commentPoster
    ) {
        this.objectMapper = objectMapper;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
        this.practiceCatalogInjector = practiceCatalogInjector;
        this.resultParser = resultParser;
        this.deliveryService = deliveryService;
        this.commentPoster = commentPoster;
    }

    @Override
    public AgentJobType jobType() {
        return AgentJobType.ISSUE_REVIEW;
    }

    @Override
    public JobSubmission createSubmission(JobSubmissionRequest request) {
        if (!(request instanceof IssueReviewSubmissionRequest r)) {
            throw new IllegalArgumentException(
                "Expected IssueReviewSubmissionRequest, got: " + request.getClass().getSimpleName()
            );
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("target_type", "ISSUE");
        metadata.put("repository_id", r.repositoryId());
        metadata.put("repository_full_name", r.repositoryFullName());
        metadata.put("issue_id", r.issueId());
        metadata.put("issue_number", r.issueNumber());
        metadata.put("title", r.title());
        metadata.put("body", r.body());
        metadata.put("state", r.state());
        // Lifecycle event that triggered this job; the injector materialises only the matching practices
        // (e.g. IssueClosed runs the retrospective set). Null = full focus set (gate-bypass dev path).
        if (r.triggerEvent() != null) {
            metadata.put("trigger_event", r.triggerEvent());
        }

        // Trailing freshness segment (mirrors the PR head-SHA slot): an edited issue gets a new key
        // and re-reviews, while extractCooldownKeyPrefix strips it so cooldown scopes per-issue — NOT
        // per-repo. Without a 4th segment the prefix would collapse to "issue_review:repo:" and block
        // every other issue in the repo.
        String version = r.updatedAt() != null ? String.valueOf(r.updatedAt().toEpochMilli()) : "0";
        // Phase before the version segment: an authoring pass (IssueCreated/Labeled) and a retrospective
        // (IssueClosed) of the same issue are different reviews and must not dedup against each other.
        String phase = r.triggerEvent() != null ? r.triggerEvent() : "manual";
        String idempotencyKey =
            "issue_review:" + r.repositoryFullName() + ":" + r.issueNumber() + ":" + phase + ":" + version;
        return new JobSubmission(metadata, idempotencyKey);
    }

    @Override
    public Map<String, byte[]> prepareInputFiles(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        Map<String, byte[]> files = new LinkedHashMap<>(
            workspaceContextBuilder.build(new ContextRequest.IssueReviewRequest(job))
        );
        files.put(WorkspaceAbi.TASK_ENVELOPE_FILENAME, taskEnvelopeWriter.write(buildTaskEnvelope(job, metadata)));
        practiceCatalogInjector.inject(files, job, WorkArtifact.ISSUE);
        log.info(
            "Issue context preparation complete: {} files, issueNumber={}, jobId={}",
            files.size(),
            metadata.path("issue_number").asInt(),
            job.getId()
        );
        return files;
    }

    private TaskEnvelope buildTaskEnvelope(AgentJob job, JsonNode metadata) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        int issueNumber = requireInt(metadata, "issue_number");
        String repoName = requireText(metadata, "repository_full_name");
        // Reuse the PracticeReview task kind — the agent is artifact-agnostic; the prompt + the
        // issue context files (context/target/issue_summary.md, metadata.json, comments.json) drive it.
        Task task = new Task.PracticeReview(buildPrompt(issueNumber, repoName, job), issueNumber, repoName);
        return TaskEnvelope.of(job.getId(), job.getWorkspace().getId(), task);
    }

    private String buildPrompt(int issueNumber, String repoName, AgentJob job) {
        String prompt =
            "Review issue #" +
            issueNumber +
            " in " +
            repoName +
            ". This is an ISSUE, not a pull request — there is no code diff. Read the issue context files " +
            "(context/target/issue_summary.md, context/target/metadata.json, context/target/comments.json), then " +
            "evaluate each practice in .practices/ against the issue and persist every justified finding via the " +
            "report_finding tool. Evidence locations should reference the issue thread/metadata, not source files. " +
            "Follow " +
            WorkspaceAbi.ORCHESTRATOR_PATH +
            " for the finding schema and rules.";
        log.info("Built issue orchestrator prompt: {} chars, jobId={}", prompt.length(), job.getId());
        return prompt;
    }

    @Override
    public void deliver(AgentJob job) {
        var parsed = resultParser.parse(job.getOutput());
        if (!parsed.discarded().isEmpty()) {
            log.info("Discarded {} findings during parsing: jobId={}", parsed.discarded().size(), job.getId());
        }
        if (parsed.validFindings().isEmpty()) {
            throw new JobDeliveryException(
                "No valid findings in agent output: jobId=" + job.getId() + ", discarded=" + parsed.discarded().size()
            );
        }
        // No diff-scope filtering: issue findings reference the thread/metadata, not diff files.
        PracticeDetectionDeliveryService.DeliveryResult result = deliveryService.deliver(job, parsed.validFindings());
        log.info(
            "Issue delivery complete: inserted={}, unknownSlug={}, duplicate={}, jobId={}",
            result.inserted(),
            result.discardedUnknownSlug(),
            result.discardedDuplicate(),
            job.getId()
        );

        PracticeDetectionResultParser.DeliveryContent delivery = DeliveryComposer.compose(
            parsed.validFindings(),
            WorkArtifact.ISSUE
        );
        postIssueNote(job, delivery);
    }

    /**
     * Posts the composed student-facing note as a comment on the GitLab issue. Best-effort: a posting
     * failure is logged, not thrown, so a transient delivery error never marks an otherwise-successful
     * detection job FAILED (mirrors {@code FeedbackDeliveryService}'s soft-failure stance). Findings are
     * already persisted above, so the formative loop is intact even if the comment does not land.
     */
    // Package-private for direct testing of the suppression + soft-failure contract (mirrors how
    // FeedbackDeliveryService.deliverFeedback is tested), without driving the real result parser.
    void postIssueNote(AgentJob job, PracticeDetectionResultParser.@Nullable DeliveryContent delivery) {
        if (delivery == null || delivery.mrNote() == null) {
            return;
        }
        JsonNode metadata = job.getMetadata();
        if (metadata != null && "closed".equalsIgnoreCase(metadata.path("state").asString(""))) {
            log.info("Issue delivery suppressed: issue closed, jobId={}", job.getId());
            return;
        }
        String sanitized = PullRequestCommentPoster.sanitize(delivery.mrNote());
        if (sanitized.isBlank()) {
            log.debug("Issue note empty after sanitization, skipping post: jobId={}", job.getId());
            return;
        }
        try {
            String formatted = FeedbackDeliveryService.formatPracticeNote(sanitized, job);
            String commentId = commentPoster.postIssueFormattedBody(job, formatted);
            if (commentId != null) {
                job.setDeliveryCommentId(commentId);
                log.info("Issue feedback posted: jobId={}, commentId={}", job.getId(), commentId);
            }
        } catch (RuntimeException e) {
            log.warn("Issue feedback delivery failed (non-fatal): jobId={}", job.getId(), e);
        }
    }

    private static String requireText(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        return node.asString();
    }

    private static int requireInt(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new JobPreparationException("Missing/invalid numeric metadata field: " + field);
        }
        return node.asInt();
    }
}

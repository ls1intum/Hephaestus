package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for {@link AgentJobType#PULL_REQUEST_REVIEW} jobs.
 *
 * <p>Prepares workspace context for an AI agent to perform practice-aware code review:
 * <ul>
 *   <li>Repository source files at the pull request's head commit</li>
 *   <li>Unified diff between target and source branches</li>
 *   <li>Pull request metadata (title, body, author, branches)</li>
 *   <li>Review comments from the database</li>
 *   <li>Practice definitions from the workspace</li>
 * </ul>
 *
 * <p>Files are injected into the container's {@code /workspace} directory:
 * <pre>
 * /workspace/
 * ├── repo/              # Source files at head commit
 * ├── .context/
 * │   ├── metadata.json  # Title, body, author, branches
 * │   ├── diff.patch     # Unified diff (target..source)
 * │   ├── comments.json  # Review comments (ordered by creation time)
 * │   └── practices.json # Practice definitions for this workspace
 * ├── .prompt            # Written by executor from buildPrompt()
 * └── .output/           # Agent writes results here
 * </pre>
 *
 * <h2>Memory budget</h2>
 * <p>{@link #prepareInputFiles} materialises all workspace files in heap before handing them
 * to the sandbox's tar injector. Repo files are copied into a new map with {@code repo/} prefixes,
 * so peak usage is ~2× {@link #MAX_REPO_BYTES} plus diff and context files (~82 MB worst-case).
 * Concurrent job count is limited by {@code SandboxProperties.maxConcurrentContainers}.
 */
public class PullRequestReviewHandler implements JobTypeHandler {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewHandler.class);

    /** Maximum total bytes for repository files (40 MB, leaving room for context files within 50 MB). */
    static final long MAX_REPO_BYTES = 40L * 1024 * 1024;

    /** Maximum diff size injected into the workspace (2 MB). Larger diffs are truncated with a note. */
    static final long MAX_DIFF_BYTES = 2L * 1024 * 1024;

    /** Maximum number of review comments included in context. Most recent are kept on truncation. */
    static final int MAX_COMMENTS = 500;

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeDetectionResultParser resultParser;
    private final PracticeDetectionDeliveryService deliveryService;

    PullRequestReviewHandler(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        PracticeRepository practiceRepository,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.practiceRepository = practiceRepository;
        this.resultParser = resultParser;
        this.deliveryService = deliveryService;
    }

    @Override
    public AgentJobType jobType() {
        return AgentJobType.PULL_REQUEST_REVIEW;
    }

    @Override
    public JobSubmission createSubmission(JobSubmissionRequest request) {
        if (!(request instanceof PullRequestReviewSubmissionRequest submissionRequest)) {
            throw new IllegalArgumentException(
                "Expected PullRequestReviewSubmissionRequest, got: " + request.getClass().getSimpleName()
            );
        }

        EventPayload.PullRequestData pullRequestData = submissionRequest.pullRequest();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", pullRequestData.repository().id());
        metadata.put("repository_full_name", pullRequestData.repository().nameWithOwner());
        metadata.put("pull_request_id", pullRequestData.id());
        metadata.put("pr_number", pullRequestData.number());
        metadata.put("pr_url", pullRequestData.htmlUrl());
        metadata.put("commit_sha", submissionRequest.headRefOid());
        metadata.put("source_branch", submissionRequest.headRefName());
        metadata.put("target_branch", submissionRequest.baseRefName());

        String idempotencyKey =
            "pr_review:" +
            pullRequestData.repository().nameWithOwner() +
            ":" +
            pullRequestData.number() +
            ":" +
            submissionRequest.headRefOid();

        return new JobSubmission(metadata, idempotencyKey);
    }

    @Override
    public Map<String, byte[]> prepareInputFiles(AgentJob job) {
        long startNanos = System.nanoTime();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        long repositoryId = requireLong(metadata, "repository_id");
        long pullRequestId = requireLong(metadata, "pull_request_id");
        String commitSha = requireText(metadata, "commit_sha");
        String sourceBranch = requireText(metadata, "source_branch");
        String targetBranch = requireText(metadata, "target_branch");

        Map<String, byte[]> files = new HashMap<>();

        // 1. Read repository source files at the pull request's head commit
        Map<String, byte[]> repoFiles;
        try {
            repoFiles = gitRepositoryManager.readFilesAtCommit(repositoryId, commitSha, MAX_REPO_BYTES);
        } catch (Exception e) {
            throw new JobPreparationException(
                "Failed to read repo files: repoId=" + repositoryId + ", commit=" + commitSha,
                e
            );
        }
        long repoBytes = 0;
        for (Map.Entry<String, byte[]> entry : repoFiles.entrySet()) {
            files.put("repo/" + entry.getKey(), entry.getValue());
            repoBytes += entry.getValue().length;
        }
        if (repoFiles.isEmpty()) {
            log.warn(
                "No repo files collected — agent will rely on diff only: repoId={}, commit={}",
                repositoryId,
                commitSha
            );
        } else {
            log.info(
                "Collected {} repo files ({} bytes) for pull request review: repoId={}, commit={}",
                repoFiles.size(),
                repoBytes,
                repositoryId,
                commitSha
            );
        }

        // 2. Generate unified diff
        String diff;
        try {
            diff = gitRepositoryManager.generateUnifiedDiff(repositoryId, targetBranch, sourceBranch);
        } catch (Exception e) {
            throw new JobPreparationException(
                "Failed to generate diff: repoId=" + repositoryId + ", base=" + targetBranch + ", head=" + sourceBranch,
                e
            );
        }
        if (diff.isEmpty()) {
            throw new JobPreparationException(
                "Empty diff — nothing to review: repoId=" +
                    repositoryId +
                    ", base=" +
                    targetBranch +
                    ", head=" +
                    sourceBranch
            );
        }
        byte[] diffBytes = diff.getBytes(StandardCharsets.UTF_8);
        if (diffBytes.length > MAX_DIFF_BYTES) {
            log.warn(
                "Diff truncated from {} to {} bytes: repoId={}, base={}, head={}",
                diffBytes.length,
                MAX_DIFF_BYTES,
                repositoryId,
                targetBranch,
                sourceBranch
            );
            // Truncate on a character boundary by decoding and re-encoding a safe prefix
            int safeLimit = findUtf8CharBoundary(diffBytes, (int) MAX_DIFF_BYTES);
            String truncated =
                new String(diffBytes, 0, safeLimit, StandardCharsets.UTF_8) +
                "\n\n[... diff truncated at 2 MB — review the full diff via the repo source files]\n";
            diffBytes = truncated.getBytes(StandardCharsets.UTF_8);
        }
        files.put(".context/diff.patch", diffBytes);
        log.info(
            "Generated diff ({} bytes): repoId={}, base={}, head={}",
            diffBytes.length,
            repositoryId,
            targetBranch,
            sourceBranch
        );

        // 3. Build pull request metadata
        ObjectNode pullRequestMetadata = buildPullRequestMetadata(pullRequestId, metadata);
        try {
            files.put(
                ".context/metadata.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pullRequestMetadata)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JobPreparationException("Failed to serialize pull request metadata", e);
        }

        // 4. Build review comments
        JsonNode comments = buildReviewComments(pullRequestId);
        try {
            files.put(
                ".context/comments.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comments)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JobPreparationException("Failed to serialize review comments", e);
        }

        // 5. Practice definitions
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();
        List<Practice> practices = practiceRepository.findByWorkspaceIdAndActiveTrue(workspaceId);
        if (practices.isEmpty()) {
            throw new JobPreparationException(
                "No active practices for workspace: workspaceId=" + workspaceId + ", jobId=" + job.getId()
            );
        }
        JsonNode practicesJson = buildPracticeDefinitions(practices);
        try {
            files.put(
                ".context/practices.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(practicesJson)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JobPreparationException("Failed to serialize practice definitions", e);
        }
        log.info(
            "Included {} active practice definitions: workspaceId={}, jobId={}",
            practices.size(),
            workspaceId,
            job.getId()
        );

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(
            "Context preparation complete: {} files, {} ms, repoId={}, pullRequestId={}",
            files.size(),
            elapsedMs,
            repositoryId,
            pullRequestId
        );
        return files;
    }

    @Override
    public String buildPrompt(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        String pullRequestUrl = requireText(metadata, "pr_url");
        int pullRequestNumber = requireInt(metadata, "pr_number");
        String repoName = requireText(metadata, "repository_full_name");

        // NOTE: practices are queried here independently of prepareInputFiles() because the
        // handler is a singleton bean — no instance state can be shared between calls.
        // The SPI does not support passing data from prepareInputFiles to buildPrompt.
        // Both calls are sequential on the same thread (AgentJobExecutor), and the query is
        // cheap (indexed on workspace_id + active). This mirrors PracticeDetectionDeliveryService
        // which also re-queries at delivery time for freshness.
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();
        List<Practice> practices = practiceRepository.findByWorkspaceIdAndActiveTrue(workspaceId);
        if (practices.isEmpty()) {
            throw new JobPreparationException(
                "No active practices for workspace: workspaceId=" + workspaceId + ", jobId=" + job.getId()
            );
        }

        // Use concatenation instead of String.formatted() — metadata fields (repo name, URL)
        // may contain '%' characters which would be misinterpreted as format specifiers.
        StringBuilder sb = new StringBuilder(4096);

        sb.append("You are an AI agent performing practice-aware code review on a pull request.\n");
        sb.append(
            "Your task is to evaluate the PR against specific engineering practices and produce structured findings.\n"
        );
        sb.append('\n');

        // -- Context
        sb.append("## Context\n");
        sb.append('\n');
        sb.append("Pull Request #").append(pullRequestNumber);
        sb.append(" in repository ").append(repoName).append('\n');
        sb.append("PR URL: ").append(pullRequestUrl).append('\n');
        sb.append('\n');

        // -- Workspace layout
        sb.append("## Workspace Layout\n");
        sb.append('\n');
        sb.append("- `/workspace/repo/` — Full source code at the PR's head commit\n");
        sb.append("- `/workspace/.context/diff.patch` — Unified diff (target branch vs source branch)\n");
        sb.append("- `/workspace/.context/metadata.json` — PR title, description, author, branches\n");
        sb.append("- `/workspace/.context/comments.json` — Existing review comments\n");
        sb.append("- `/workspace/.context/practices.json` — Practice definitions (structured, see below)\n");
        sb.append('\n');

        // -- Practice definitions (inline in prompt for direct agent consumption)
        sb.append("## Practices to Evaluate\n");
        sb.append('\n');
        sb.append("Evaluate the PR against each of the following practices. ");
        sb.append("Produce exactly one finding per practice.\n");
        sb.append('\n');
        for (Practice p : practices) {
            sb.append("### ").append(p.getSlug()).append(": ").append(p.getName()).append('\n');
            if (p.getCategory() != null) {
                sb.append("Category: ").append(p.getCategory()).append('\n');
            }
            // Prefer detectionPrompt (agent-specific instructions); fall back to description
            String instructions = p.getDetectionPrompt() != null ? p.getDetectionPrompt() : p.getDescription();
            if (instructions != null) {
                sb.append(instructions).append('\n');
            }
            sb.append('\n');
        }

        // -- Instructions
        sb.append("## Instructions\n");
        sb.append('\n');
        sb.append("1. Read the diff at `/workspace/.context/diff.patch` to understand what changed\n");
        sb.append("2. Examine relevant source files in `/workspace/repo/` for full context\n");
        sb.append("3. Review the PR metadata at `/workspace/.context/metadata.json`\n");
        sb.append("4. For EACH practice listed above, produce exactly one finding\n");
        sb.append("5. Focus your evaluation on the CHANGED code (lines in the diff). ");
        sb.append("Pre-existing code outside the diff is context only, not a finding target.\n");
        sb.append("6. When uncertain, prefer NOT_APPLICABLE or NEEDS_REVIEW over a false NEGATIVE. ");
        sb.append("Precision is more valuable than recall.\n");
        sb.append('\n');
        sb.append("Verdict meanings:\n");
        sb.append("- `POSITIVE`: the contributor followed this practice\n");
        sb.append("- `NEGATIVE`: the contributor violated or missed this practice\n");
        sb.append("- `NOT_APPLICABLE`: the practice does not apply to this PR's changes ");
        sb.append("(use severity `INFO` and confidence `1.0`; evidence and guidance may be omitted)\n");
        sb.append("- `NEEDS_REVIEW`: borderline case where your confidence is below 0.6\n");
        sb.append('\n');

        // -- Output contract (must match PracticeDetectionResultParser expectations exactly)
        // NOTE: The JSON schema is shown WITHOUT markdown fences (no ```json wrapper)
        // because LLMs tend to imitate example formatting — fences in the example cause
        // the agent to wrap its output in fences, which breaks JSON parsing.
        sb.append("## Output\n");
        sb.append('\n');
        sb.append("Write ONLY a JSON object to `/workspace/.output/result.json`.\n");
        sb.append("Start the file with `{` and end with `}` — no surrounding text, ");
        sb.append("no markdown fences, no code blocks, no commentary.\n");
        sb.append('\n');
        sb.append("Required structure:\n");
        sb.append("{\n");
        sb.append("  \"findings\": [\n");
        sb.append("    {\n");
        sb.append("      \"practiceSlug\": \"string (required, must match a practice slug above)\",\n");
        sb.append("      \"title\": \"string (required, concise finding summary, max 255 chars)\",\n");
        sb.append("      \"verdict\": \"POSITIVE | NEGATIVE | NOT_APPLICABLE | NEEDS_REVIEW\",\n");
        sb.append("      \"severity\": \"CRITICAL | MAJOR | MINOR | INFO\",\n");
        sb.append("      \"confidence\": 0.95,\n");
        sb.append("      \"evidence\": {\n");
        sb.append("        \"locations\": [{\"path\": \"src/File.java\", \"startLine\": 10, \"endLine\": 20}],\n");
        sb.append("        \"snippets\": [\"relevant code snippet\"]\n");
        sb.append("      },\n");
        sb.append("      \"reasoning\": \"string (optional, detailed explanation)\",\n");
        sb.append("      \"guidance\": \"string (optional, actionable advice for the contributor)\",\n");
        sb.append(
            "      \"guidanceMethod\": \"MODELING | COACHING | SCAFFOLDING | ARTICULATION | REFLECTION | EXPLORATION (optional)\"\n"
        );
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append('\n');
        sb.append("Field rules:\n");
        sb.append(
            "- `practiceSlug` must match one of the slugs above (case-insensitive; underscores treated as hyphens)\n"
        );
        sb.append("- Produce exactly one finding per practice (").append(practices.size()).append(" total)\n");
        sb.append("- `confidence` is a float in [0.0, 1.0] — not a percentage\n");
        sb.append("- `evidence` is optional; when provided, `locations[].path` is relative to repo root\n");
        // NOTE: Prompt limits (2K/1K) are intentionally stricter than parser limits (10K/5K).
        // This keeps agent output concise while the parser's higher caps provide tolerance.
        sb.append("- `reasoning` should be under 2,000 characters; `guidance` under 1,000 characters\n");
        sb.append("- `guidanceMethod` selects a cognitive apprenticeship method appropriate for the finding\n");
        sb.append("- Severity describes importance, not whether the practice was followed. ");
        sb.append(
            "Example: verdict=POSITIVE severity=MAJOR means the contributor correctly handled a critical practice; "
        );
        sb.append("verdict=NEGATIVE severity=MINOR means a low-impact style violation\n");

        String prompt = sb.toString();
        log.info(
            "Built practice-aware prompt: {} chars, {} practices, workspaceId={}, jobId={}",
            prompt.length(),
            practices.size(),
            workspaceId,
            job.getId()
        );
        return prompt;
    }

    @Override
    public void deliver(AgentJob job) {
        var parsed = resultParser.parse(job.getOutput());
        if (!parsed.discarded().isEmpty()) {
            log.info(
                "Discarded {} findings during parsing: jobId={}, reasons={}",
                parsed.discarded().size(),
                job.getId(),
                parsed.discarded()
            );
        }
        if (parsed.validFindings().isEmpty()) {
            throw new JobDeliveryException(
                "No valid findings in agent output: jobId=" + job.getId() + ", discarded=" + parsed.discarded().size()
            );
        }
        try {
            var result = deliveryService.deliver(job, parsed.validFindings());
            log.info(
                "Delivery complete: inserted={}, unknownSlug={}, overCap={}, duplicate={}, jobId={}",
                result.inserted(),
                result.discardedUnknownSlug(),
                result.discardedOverCap(),
                result.discardedDuplicate(),
                job.getId()
            );
        } catch (JobDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new JobDeliveryException("Delivery failed unexpectedly: jobId=" + job.getId(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ObjectNode buildPullRequestMetadata(long pullRequestId, JsonNode jobMetadata) {
        ObjectNode result = objectMapper.createObjectNode();

        // Copy routing fields from job metadata (always present — validated in prepareInputFiles)
        result.put("pr_number", requireInt(jobMetadata, "pr_number"));
        result.put("pr_url", requireText(jobMetadata, "pr_url"));
        result.put("repository_full_name", requireText(jobMetadata, "repository_full_name"));
        result.put("source_branch", requireText(jobMetadata, "source_branch"));
        result.put("target_branch", requireText(jobMetadata, "target_branch"));
        result.put("commit_sha", requireText(jobMetadata, "commit_sha"));

        // Enrich from current DB state (title, body, author, etc.)
        PullRequest pullRequest = pullRequestRepository.findByIdWithRepository(pullRequestId).orElse(null);
        if (pullRequest == null) {
            log.warn("Pull request not found in database during context preparation: pullRequestId={}", pullRequestId);
            result.put("enriched", false);
            return result;
        }
        result.put("enriched", true);
        result.put("title", pullRequest.getTitle());
        result.put("body", pullRequest.getBody());
        if (pullRequest.getState() != null) {
            result.put("state", pullRequest.getState().name());
        }
        result.put("is_draft", pullRequest.isDraft());
        result.put("additions", pullRequest.getAdditions());
        result.put("deletions", pullRequest.getDeletions());
        result.put("changed_files", pullRequest.getChangedFiles());
        if (pullRequest.getAuthor() != null) {
            result.put("author", pullRequest.getAuthor().getLogin());
        }

        return result;
    }

    private JsonNode buildPracticeDefinitions(List<Practice> practices) {
        var array = objectMapper.createArrayNode();
        for (Practice p : practices) {
            var node = objectMapper.createObjectNode();
            node.put("slug", p.getSlug());
            node.put("name", p.getName());
            if (p.getCategory() != null) {
                node.put("category", p.getCategory());
            }
            if (p.getDescription() != null) {
                node.put("description", p.getDescription());
            }
            if (p.getDetectionPrompt() != null) {
                node.put("detection_prompt", p.getDetectionPrompt());
            }
            array.add(node);
        }
        return array;
    }

    private JsonNode buildReviewComments(long pullRequestId) {
        var comments = reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(pullRequestId);
        log.debug("Fetched {} review comments for pull request: pullRequestId={}", comments.size(), pullRequestId);
        if (comments.size() > MAX_COMMENTS) {
            log.warn(
                "Truncating review comments from {} to {}: pullRequestId={}",
                comments.size(),
                MAX_COMMENTS,
                pullRequestId
            );
            comments = comments.subList(comments.size() - MAX_COMMENTS, comments.size());
        }
        var commentsArray = objectMapper.createArrayNode();
        for (var comment : comments) {
            var commentNode = objectMapper.createObjectNode();
            commentNode.put("path", comment.getPath());
            commentNode.put("line", comment.getLine());
            commentNode.put("body", comment.getBody());
            if (comment.getCreatedAt() != null) {
                commentNode.put("created_at", comment.getCreatedAt().toString());
            }
            if (comment.getAuthor() != null) {
                commentNode.put("author", comment.getAuthor().getLogin());
            }
            commentsArray.add(commentNode);
        }
        return commentsArray;
    }

    /**
     * Extract a required text field from job metadata.
     *
     * @throws JobPreparationException if the field is missing or blank
     */
    private static String requireText(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        return node.asText();
    }

    /**
     * Extract a required integer field from job metadata.
     *
     * @throws JobPreparationException if the field is missing or not a number
     */
    private static int requireInt(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        if (!node.isNumber()) {
            throw new JobPreparationException(
                "Expected numeric metadata field: " + field + ", got: " + node.getNodeType()
            );
        }
        return node.asInt();
    }

    /**
     * Extract a required numeric field from job metadata.
     *
     * @throws JobPreparationException if the field is missing or not a number
     */
    private static long requireLong(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        if (!node.isNumber()) {
            throw new JobPreparationException(
                "Expected numeric metadata field: " + field + ", got: " + node.getNodeType()
            );
        }
        return node.asLong();
    }

    /**
     * Find the largest byte offset ≤ limit that does not split a multi-byte UTF-8 character.
     *
     * <p>UTF-8 continuation bytes start with {@code 10xxxxxx} (0x80..0xBF). Walking backwards
     * from the limit skips any continuation bytes that would be orphaned by the cut.
     */
    static int findUtf8CharBoundary(byte[] data, int limit) {
        if (limit >= data.length) {
            return data.length;
        }
        int pos = limit;
        // Walk backwards past continuation bytes (0x80..0xBF)
        while (pos > 0 && (data[pos] & 0xC0) == 0x80) {
            pos--;
        }
        return pos;
    }
}

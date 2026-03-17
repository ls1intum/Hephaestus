package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for {@link AgentJobType#PULL_REQUEST_REVIEW} jobs.
 *
 * <p>Prepares workspace context for an AI agent to review a pull request:
 * <ul>
 *   <li>Repository source files at the pull request's head commit</li>
 *   <li>Unified diff between target and source branches</li>
 *   <li>Pull request metadata (title, body, author, branches)</li>
 *   <li>Review comments from the database</li>
 * </ul>
 *
 * <p>Files are injected into the container's {@code /workspace} directory:
 * <pre>
 * /workspace/
 * ├── repo/              # Source files at head commit
 * ├── .context/
 * │   ├── metadata.json  # Title, body, author, branches
 * │   ├── diff.patch     # Unified diff (target..source)
 * │   └── comments.json  # Review comments (ordered by creation time)
 * ├── .prompt            # Written by executor from buildPrompt()
 * └── .output/           # Agent writes results here
 * </pre>
 *
 * <h2>Memory budget</h2>
 * <p>{@link #prepareInputFiles} materialises all workspace files in heap before handing them
 * to the sandbox's tar injector. Peak usage per job is bounded by {@link #MAX_REPO_BYTES}
 * plus diff and context files (~42 MB worst-case). Concurrent job count is limited by
 * {@code SandboxProperties.maxConcurrentContainers}.
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

    PullRequestReviewHandler(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewCommentRepository = reviewCommentRepository;
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

        // Use concatenation instead of String.formatted() — metadata fields (repo name, URL)
        // may contain '%' characters which would be misinterpreted as format specifiers.
        return (
            "You are a senior software engineer performing a thorough code review.\n" +
            "\n" +
            "## Context\n" +
            "\n" +
            "You are reviewing Pull Request #" +
            pullRequestNumber +
            " in repository " +
            repoName +
            ".\n" +
            "PR URL: " +
            pullRequestUrl +
            "\n" +
            "\n" +
            "## Workspace Layout\n" +
            "\n" +
            "- `/workspace/repo/` — Full source code at the PR's head commit\n" +
            "- `/workspace/.context/diff.patch` — Unified diff (target branch vs source branch)\n" +
            "- `/workspace/.context/metadata.json` — PR title, description, author, branches\n" +
            "- `/workspace/.context/comments.json` — Existing review comments\n" +
            "\n" +
            "## Instructions\n" +
            "\n" +
            "1. Read the diff to understand what changed\n" +
            "2. Examine the relevant source files in `/workspace/repo/` for full context\n" +
            "3. Review the PR metadata and existing comments\n" +
            "4. Provide a thorough code review covering:\n" +
            "   - Correctness and potential bugs\n" +
            "   - Security concerns\n" +
            "   - Performance implications\n" +
            "   - Code style and best practices\n" +
            "   - Missing test coverage\n" +
            "\n" +
            "## Output\n" +
            "\n" +
            "Write your review as a JSON file to `/workspace/.output/result.json` with this structure:\n" +
            "```json\n" +
            "{\n" +
            "  \"summary\": \"Brief overall assessment\",\n" +
            "  \"review_comment\": \"Detailed markdown review suitable for posting as a PR comment\",\n" +
            "  \"severity\": \"info|warning|critical\",\n" +
            "  \"suggestions\": [\n" +
            "    {\n" +
            "      \"file\": \"path/to/file\",\n" +
            "      \"line\": 42,\n" +
            "      \"suggestion\": \"Description of the suggestion\"\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "```\n"
        );
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

package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import de.tum.in.www1.hephaestus.practices.finding.ContributorHistoryProvider;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Handler for {@link AgentJobType#PULL_REQUEST_REVIEW} jobs.
 *
 * <p>Uses an orchestrator + subagent architecture: a main Claude Code agent reads CLAUDE.md,
 * decides which practices are relevant, then spawns parallel subagents (one per practice)
 * that each write findings to {@code .analysis/practices/{slug}.json}. The orchestrator
 * collects, cross-references, and returns final structured JSON.
 *
 * <p>Container workspace layout:
 * <pre>
 * /workspace/
 * ├── repo/                              # Real git repo (read-only bind mount)
 * ├── .context/
 * │   ├── metadata.json                  # Title, body, author, branches, stats
 * │   ├── comments.json                  # Review comments
 * │   ├── diff.patch                     # Pre-computed diff with [L&lt;n&gt;] annotations
 * │   ├── diff_stat.txt                  # Changed files summary
 * │   └── contributor_history.json       # Aggregated practice history (optional)
 * ├── .practices/
 * │   ├── index.json                     # Practice registry [{slug, name, category}]
 * │   └── {slug}.md                      # Evaluation criteria per practice
 * ├── .analysis/practices/               # Subagents write findings here
 * ├── CLAUDE.md                          # Orchestrator meta-instructions
 * ├── .claude/agents/practice-analyzer.md # Subagent definition
 * ├── .prompt                            # Slim task prompt
 * ├── .json-schema                       # Output schema for constrained decoding
 * └── .output/                           # Agent writes final results here
 * </pre>
 */
public class PullRequestReviewHandler implements JobTypeHandler {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewHandler.class);

    /** Maximum number of review comments included in context. Most recent are kept on truncation. */
    static final int MAX_COMMENTS = 500;

    /** Classpath prefix for agent resource files (CLAUDE.md, subagent def, practices). */
    private static final String AGENT_RESOURCE_PREFIX = "agent/";

    /** Regex matching unified diff hunk headers: {@code @@ -a,b +c,d @@}. Group 1 captures {@code c}. */
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    // Security scan patterns for pre-computed secret detection.
    // These run server-side in Java so they work regardless of container resource limits.

    /** Keywords commonly associated with secrets/credentials in source code. */
    private static final Pattern SECURITY_KEYWORDS = Pattern.compile(
        "(?i)(api[_-]?key|apikey|token|secret|password|credential|bearer|authorization|private[_-]?key|access[_-]?key)"
    );

    /** High-entropy strings in quotes (≥20 chars, alphanumeric/base64) — likely real keys. */
    private static final Pattern HIGH_ENTROPY_STRING = Pattern.compile("\"[A-Za-z0-9+/=_\\-]{20,}\"");

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final PracticeRepository practiceRepository;
    private final ContributorHistoryProvider contributorHistoryProvider;
    private final PracticeDetectionResultParser resultParser;
    private final PracticeDetectionDeliveryService deliveryService;
    private final FeedbackDeliveryService feedbackService;

    PullRequestReviewHandler(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        PracticeRepository practiceRepository,
        ContributorHistoryProvider contributorHistoryProvider,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.practiceRepository = practiceRepository;
        this.contributorHistoryProvider = contributorHistoryProvider;
        this.resultParser = resultParser;
        this.deliveryService = deliveryService;
        this.feedbackService = feedbackService;
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

    /**
     * Prepare context files for the agent. Only DB-sourced data is injected as files —
     * the repository itself is bind-mounted via {@link #volumeMounts(AgentJob)}.
     */
    @Override
    public Map<String, byte[]> prepareInputFiles(AgentJob job) {
        long startNanos = System.nanoTime();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        long repositoryId = requireLong(metadata, "repository_id");
        long pullRequestId = requireLong(metadata, "pull_request_id");

        Map<String, byte[]> files = new HashMap<>();

        // Ensure repo is cloned/fetched before volumeMounts() resolves the path
        String gitToken = job.getWorkspace() != null ? job.getWorkspace().getPersonalAccessToken() : null;
        ensureRepositoryCloned(metadata, repositoryId, gitToken);

        // Load PR entity once — shared by metadata, comments, and contributor history
        PullRequest pullRequest = pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);

        // Inject DB-sourced context (metadata + comments + contributor history)
        storeMetadataAndComments(files, pullRequest, pullRequestId, metadata);
        storeContributorHistory(files, pullRequest, job);

        // Pre-compute diff: source branches may be deleted after merge,
        // so we compute the diff from the merge commit graph using SHAs.
        computeAndStoreDiff(files, repositoryId, metadata);

        // Build a structured per-file diff summary optimized for single-pass AI consumption.
        // This is structural transformation (splitting on "diff --git" boundaries), not judgment.
        computeAndStoreDiffSummary(files);

        // Inject orchestrator architecture files (CLAUDE.md, subagent def, practice criteria)
        injectOrchestratorFiles(files, job);

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

    /**
     * Mount the real locally-cloned git repository into the container read-only.
     * The agent gets full access to git history, blame, diffs, and the complete codebase.
     */
    @Override
    public Map<String, String> volumeMounts(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        long repositoryId = requireLong(metadata, "repository_id");

        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            throw new JobPreparationException(
                "Repository not cloned: repoId=" + repositoryId + ", jobId=" + job.getId()
            );
        }

        log.info("Mounting real repo: repoId={}, path={}", repositoryId, repoPath);
        return Map.of(repoPath.toAbsolutePath().toString(), "/workspace/repo");
    }

    @Override
    public String buildPrompt(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        int pullRequestNumber = requireInt(metadata, "pr_number");
        String repoName = requireText(metadata, "repository_full_name");

        // Slim task prompt — orchestrator instructions are in agent-specific files
        // (CLAUDE.md for Claude Code, agent def for OpenCode), which reference orchestrator-protocol.md.
        String prompt =
            "Review merge request #" +
            pullRequestNumber +
            " in " +
            repoName +
            ". Follow the orchestrator protocol in /workspace/orchestrator-protocol.md.";
        log.info("Built orchestrator prompt: {} chars, jobId={}", prompt.length(), job.getId());
        return prompt;
    }

    // -------------------------------------------------------------------------
    // Orchestrator file injection
    // -------------------------------------------------------------------------

    /**
     * Inject the orchestrator architecture files into the workspace.
     * These files drive the Claude Code agent's behaviour:
     * <ul>
     *   <li>{@code CLAUDE.md} — orchestrator meta-instructions</li>
     *   <li>{@code .claude/agents/practice-analyzer.md} — subagent definition</li>
     *   <li>{@code .practices/index.json} — practice registry</li>
     *   <li>{@code .practices/{slug}.md} — evaluation criteria per practice</li>
     * </ul>
     */
    private void injectOrchestratorFiles(Map<String, byte[]> files, AgentJob job) {
        // 1. Shared orchestrator protocol (referenced by both Claude Code and OpenCode orchestrators)
        files.put("orchestrator-protocol.md", loadClasspathResource("orchestrator-protocol.md"));

        // Agent-specific orchestrator files (CLAUDE.md, subagent defs) are injected by each adapter.
        // The handler only injects shared context: protocol, practices, and analysis directory.

        // 2. Practice criteria files from DB
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

        // Build index.json registry
        StringBuilder indexJson = new StringBuilder("[\n");
        for (int i = 0; i < practices.size(); i++) {
            Practice p = practices.get(i);
            if (i > 0) indexJson.append(",\n");
            indexJson
                .append("  {\"slug\": \"")
                .append(escapeJson(p.getSlug()))
                .append("\", \"name\": \"")
                .append(escapeJson(p.getName()))
                .append("\", \"category\": \"")
                .append(escapeJson(p.getCategory() != null ? p.getCategory() : ""))
                .append("\"}");
        }
        indexJson.append("\n]");
        files.put(".practices/index.json", indexJson.toString().getBytes(StandardCharsets.UTF_8));

        // Generate .practices/{slug}.md for each active practice,
        // plus a bundled all-criteria.md to reduce agent tool calls
        StringBuilder bundle = new StringBuilder();
        for (Practice p : practices) {
            String criteria = p.getCriteria() != null ? p.getCriteria() : p.getDescription();
            if (criteria == null) criteria = "";
            files.put(".practices/" + p.getSlug() + ".md", criteria.getBytes(StandardCharsets.UTF_8));
            bundle.append("# ").append(p.getSlug()).append("\n\n").append(criteria).append("\n\n---\n\n");
        }
        files.put(".practices/all-criteria.md", bundle.toString().getBytes(StandardCharsets.UTF_8));

        // 4. Ensure .analysis/practices/ exists (empty marker file)
        files.put(".analysis/practices/.gitkeep", new byte[0]);

        log.info(
            "Injected orchestrator files: {} practices, workspaceId={}, jobId={}",
            practices.size(),
            workspaceId,
            job.getId()
        );
    }

    /**
     * Annotate a unified diff with {@code [L<n>]} source-file line number prefixes.
     * This eliminates the line-number offset bug where agents use patch-file positions
     * instead of actual source line numbers.
     *
     * <p>Each added ({@code +}) and context line gets a {@code [L<n>]} prefix derived
     * from the hunk header {@code @@ -a,b +c,d @@}. Deleted ({@code -}) lines and
     * diff metadata lines are left unmodified.
     */
    static String annotateDiffWithLineNumbers(String diff) {
        String[] lines = diff.split("\n", -1);
        StringBuilder out = new StringBuilder(diff.length() + lines.length * 6);
        Integer newLineNum = null;

        for (String line : lines) {
            Matcher m = HUNK_HEADER.matcher(line);
            if (m.find()) {
                newLineNum = Integer.parseInt(m.group(1));
                out.append(line).append('\n');
                continue;
            }

            if (newLineNum == null) {
                // Diff metadata (diff --git, ---, +++, etc.)
                out.append(line).append('\n');
                continue;
            }

            if (line.startsWith("+")) {
                out.append("[L").append(newLineNum).append("] ").append(line).append('\n');
                newLineNum++;
            } else if (line.startsWith("-")) {
                // Deleted lines don't increment new-file counter
                out.append(line).append('\n');
            } else {
                // Context line
                out.append("[L").append(newLineNum).append("] ").append(line).append('\n');
                newLineNum++;
            }
        }

        return out.toString();
    }

    /** Load a classpath resource from the {@code agent/} directory. */
    private byte[] loadClasspathResource(String relativePath) {
        String fullPath = AGENT_RESOURCE_PREFIX + relativePath;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new JobPreparationException("Missing classpath resource: " + fullPath);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new JobPreparationException("Failed to read classpath resource: " + fullPath, e);
        }
    }

    /** Minimal JSON string escaping for index.json generation. */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    @Override
    public void deliver(AgentJob job) {
        // 1. Parse findings + delivery content
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

        // 1b. Filter findings by diff scope — remove findings whose evidence locations
        // reference files not in the diff_stat. This is a server-side safety net for
        // LLM scope contamination (flagging pre-existing code in partially-modified files).
        Set<String> diffFiles = computeDiffStatFiles(job);
        var scopedFindings = filterByDiffScope(parsed.validFindings(), diffFiles);
        if (scopedFindings.size() < parsed.validFindings().size()) {
            log.info(
                "Diff scope filter removed {} out-of-scope findings: jobId={}, before={}, after={}",
                parsed.validFindings().size() - scopedFindings.size(),
                job.getId(),
                parsed.validFindings().size(),
                scopedFindings.size()
            );
        }
        if (scopedFindings.isEmpty()) {
            throw new JobDeliveryException("All findings filtered by diff scope: jobId=" + job.getId());
        }

        // 2. Persist findings (hard failure — must succeed)
        PracticeDetectionDeliveryService.DeliveryResult result;
        try {
            result = deliveryService.deliver(job, scopedFindings);
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

        // 3. Compose delivery content if agent didn't produce one (two-step architecture:
        //    agent outputs findings only, server renders the MR comment from structured data).
        //    The parser may return a delivery with only diffNotes (from suggestedDiffNotes fallback)
        //    but no mrNote — in that case, compose the mrNote server-side and merge diffNotes.
        PracticeDetectionResultParser.DeliveryContent delivery = parsed.delivery();
        if (delivery == null || delivery.mrNote() == null) {
            var composed = DeliveryComposer.compose(scopedFindings);
            if (composed != null) {
                // Merge: use composed mrNote, prefer existing diffNotes if present
                var diffNotes = (delivery != null && !delivery.diffNotes().isEmpty())
                    ? delivery.diffNotes()
                    : composed.diffNotes();
                delivery = new PracticeDetectionResultParser.DeliveryContent(composed.mrNote(), diffNotes);
                log.info(
                    "Server-side delivery composed from {} findings: jobId={}",
                    scopedFindings.size(),
                    job.getId()
                );
            }
        }

        // 3b. Validate and correct diff note positions against actual diff hunks.
        //     This prevents "line outside diff hunk" errors at the GitLab API level.
        if (delivery != null && !delivery.diffNotes().isEmpty()) {
            var validLines = computeDiffValidLines(job);
            if (!validLines.isEmpty()) {
                var correctedNotes = DiffHunkValidator.validateAndCorrect(
                    delivery.diffNotes(),
                    validLines,
                    job.getId().toString()
                );
                delivery = new PracticeDetectionResultParser.DeliveryContent(delivery.mrNote(), correctedNotes);
            }
        }

        // 4. Post feedback to PR/MR (soft failure — logged, not thrown)
        feedbackService.deliverFeedback(job, delivery, result.hasNegative());
    }

    // -------------------------------------------------------------------------
    // Input file preparation helpers
    // -------------------------------------------------------------------------

    /**
     * Ensure the repository is cloned/fetched locally. Throws on failure because the bind-mount
     * approach requires a valid local clone — there is no fallback.
     */
    private void ensureRepositoryCloned(JsonNode metadata, long repositoryId, @Nullable String gitToken) {
        if (!gitRepositoryManager.isEnabled()) {
            throw new JobPreparationException(
                "Git local checkout is disabled but required for bind-mount: repoId=" + repositoryId
            );
        }
        String repoFullName = requireText(metadata, "repository_full_name");

        // Derive clone URL from PR URL (works for both GitHub and GitLab).
        // PR URL format: https://<host>/<path>/-/merge_requests/<iid> (GitLab)
        //                https://<host>/<path>/pull/<number> (GitHub)
        String prUrl = requireText(metadata, "pr_url");
        String cloneUrl;
        int mrIdx = prUrl.indexOf("/-/merge_requests/");
        int pullIdx = prUrl.indexOf("/pull/");
        if (mrIdx > 0) {
            cloneUrl = prUrl.substring(0, mrIdx) + ".git";
        } else if (pullIdx > 0) {
            cloneUrl = prUrl.substring(0, pullIdx) + ".git";
        } else {
            // Fallback: construct from repo name (GitHub assumed)
            cloneUrl = "https://github.com/" + repoFullName + ".git";
        }
        try {
            gitRepositoryManager.ensureRepository(repositoryId, cloneUrl, gitToken);
        } catch (Exception e) {
            throw new JobPreparationException(
                "Failed to clone/fetch repository for bind-mount: repoId=" + repositoryId,
                e
            );
        }
    }

    /** Build and store pull request metadata and review comments as context JSON files. */
    private void storeMetadataAndComments(
        Map<String, byte[]> files,
        PullRequest pullRequest,
        long pullRequestId,
        JsonNode metadata
    ) {
        ObjectNode pullRequestMetadata = buildPullRequestMetadata(pullRequest, metadata);
        try {
            files.put(
                ".context/metadata.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pullRequestMetadata)
            );
        } catch (JsonProcessingException e) {
            throw new JobPreparationException("Failed to serialize pull request metadata", e);
        }

        JsonNode comments = buildReviewComments(pullRequestId);
        try {
            files.put(
                ".context/comments.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comments)
            );
        } catch (JsonProcessingException e) {
            throw new JobPreparationException("Failed to serialize review comments", e);
        }
    }

    /**
     * Build and store aggregated contributor practice history as a context JSON file.
     *
     * <p>Skips silently if the PR has no author or the contributor has no prior findings —
     * the absence of the file signals a first-time contributor to the agent.
     *
     * <p>Note: the contributor is always the PR author for PULL_REQUEST_REVIEW jobs.
     * If a future job type evaluates reviewers, this lookup must change.
     */
    private void storeContributorHistory(Map<String, byte[]> files, PullRequest pullRequest, AgentJob job) {
        if (pullRequest == null || pullRequest.getAuthor() == null || job.getWorkspace() == null) {
            if (pullRequest != null && pullRequest.getAuthor() == null) {
                log.debug("Skipping contributor history: PR has no author, pullRequestId={}", pullRequest.getId());
            }
            return;
        }
        Long contributorId = pullRequest.getAuthor().getId();
        Long workspaceId = job.getWorkspace().getId();

        try {
            Optional<byte[]> historyJson = contributorHistoryProvider.buildHistoryJson(contributorId, workspaceId);
            historyJson.ifPresent(json -> {
                files.put(".context/contributor_history.json", json);
                log.info(
                    "Injected contributor history: {} bytes, contributorId={}, workspaceId={}",
                    json.length,
                    contributorId,
                    workspaceId
                );
            });
        } catch (Exception e) {
            log.warn(
                "Failed to build contributor history, continuing without it: contributorId={}, workspaceId={}",
                contributorId,
                workspaceId,
                e
            );
        }
    }

    /**
     * Pre-compute the MR diff and inject it as context files. Source branches may be deleted
     * after merge, so we resolve the diff from the merge commit graph using the head SHA.
     *
     * <p>Strategy: find the merge commit whose second parent is the head SHA, then diff
     * between merge^1 (target before merge) and merge^2 (MR tip). Falls back to
     * target_branch..head_sha if branch still exists.
     */
    private void computeAndStoreDiff(Map<String, byte[]> files, long repositoryId, JsonNode metadata) {
        String headSha = metadata.has("commit_sha") ? metadata.get("commit_sha").asText() : null;
        String targetBranch = requireText(metadata, "target_branch");
        String sourceBranch = requireText(metadata, "source_branch");
        if (headSha == null || headSha.isBlank()) {
            log.warn("No commit_sha in metadata, skipping diff pre-computation");
            return;
        }
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
        try {
            // Strategy 1: Try branch-based diff (works if source branch still exists)
            String diffStat = runGit(repoPath, "diff", "--stat", "origin/" + targetBranch + "..origin/" + sourceBranch);
            String diff;
            if (diffStat != null && !diffStat.isBlank()) {
                diff = runGit(repoPath, "diff", "origin/" + targetBranch + "..origin/" + sourceBranch);
            } else {
                // Strategy 2: Find merge commit and diff its parents
                // Look for merge commit that has headSha as second parent
                String mergeCommit = runGit(
                    repoPath,
                    "log",
                    "--all",
                    "--merges",
                    "--format=%H %P",
                    "--ancestry-path",
                    headSha + "..origin/" + targetBranch
                );
                String base = null;
                if (mergeCommit != null) {
                    for (String line : mergeCommit.split("\n")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3 && parts[2].startsWith(headSha.substring(0, 8))) {
                            base = parts[1]; // First parent = target before merge
                            break;
                        }
                    }
                }
                if (base == null) {
                    // Strategy 3: Use merge-base with HEAD (last resort)
                    base = runGit(repoPath, "merge-base", "origin/" + targetBranch, headSha);
                    if (base != null) base = base.trim();
                }
                if (base != null && !base.equals(headSha)) {
                    diffStat = runGit(repoPath, "diff", "--stat", base + ".." + headSha);
                    diff = runGit(repoPath, "diff", base + ".." + headSha);
                } else {
                    log.warn("Could not compute diff base for headSha={}, targetBranch={}", headSha, targetBranch);
                    return;
                }
            }
            if (diff != null && !diff.isBlank()) {
                // Annotate diff with [L<n>] source-file line numbers so subagents
                // can reference correct line numbers without computing offsets.
                String annotatedDiff = annotateDiffWithLineNumbers(diff);
                files.put(".context/diff.patch", annotatedDiff.getBytes(StandardCharsets.UTF_8));
                if (diffStat != null) {
                    files.put(".context/diff_stat.txt", diffStat.getBytes(StandardCharsets.UTF_8));
                }
                log.info(
                    "Pre-computed diff: {} bytes (annotated: {} bytes), diffStat={} bytes, headSha={}",
                    diff.length(),
                    annotatedDiff.length(),
                    diffStat != null ? diffStat.length() : 0,
                    headSha
                );
            } else {
                throw new JobPreparationException(
                    "Empty diff: no changed files between target and head. headSha=" +
                        headSha +
                        ", targetBranch=" +
                        targetBranch +
                        ", sourceBranch=" +
                        sourceBranch
                );
            }
        } catch (JobPreparationException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                "Failed to pre-compute diff, agent will compute its own: headSha={}, error={}",
                headSha,
                e.getMessage()
            );
        }
    }

    /**
     * Build a structured per-file diff summary optimized for single-pass AI consumption.
     *
     * <p>This is pure structural transformation — splitting on {@code diff --git} boundaries
     * and formatting into indexed sections. No quality judgments. The AI reads this once
     * and evaluates all practices in a single pass, eliminating the need for grep commands
     * or subagent spawning.
     *
     * <p>For each file, a quick-scan column lists tokens found on {@code +} lines
     * (e.g., {@code try?, @State, fatalError}) so the AI can prioritize without scanning.
     */
    void computeAndStoreDiffSummary(Map<String, byte[]> files) {
        byte[] diffBytes = files.get(".context/diff.patch");
        if (diffBytes == null || diffBytes.length == 0) {
            return;
        }

        String diff = new String(diffBytes, StandardCharsets.UTF_8);

        // Split diff on "diff --git" boundaries
        List<String> fileDiffs = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String currentPath = null;

        for (String line : diff.split("\n", -1)) {
            // Match "diff --git" at line start, or after [L<n>] annotation prefix.
            // The annotation logic adds [L<n>] to context lines between files.
            String effectiveLine = line;
            if (line.startsWith("[L") && line.contains("] diff --git")) {
                effectiveLine = line.substring(line.indexOf("] ") + 2);
            }
            if (effectiveLine.startsWith("diff --git")) {
                if (currentPath != null) {
                    fileDiffs.add(currentChunk.toString());
                    filePaths.add(currentPath);
                }
                currentChunk = new StringBuilder();
                int bIdx = effectiveLine.lastIndexOf(" b/");
                currentPath = bIdx > 0 ? effectiveLine.substring(bIdx + 3) : effectiveLine;
            }
            currentChunk.append(line).append('\n');
        }
        if (currentPath != null) {
            fileDiffs.add(currentChunk.toString());
            filePaths.add(currentPath);
        }

        // Build structured summary
        StringBuilder summary = new StringBuilder();
        summary.append("# Diff Summary\n\n");
        summary.append("**").append(filePaths.size()).append(" files changed**\n\n");

        // File index with quick-scan tokens
        summary.append("| # | File | +Lines | Quick scan |\n");
        summary.append("|---|------|--------|------------|\n");
        for (int i = 0; i < filePaths.size(); i++) {
            String content = fileDiffs.get(i);
            int added = countAddedLines(content);
            String tokens = quickScanTokens(content);
            summary
                .append("| ")
                .append(i + 1)
                .append(" | `")
                .append(filePaths.get(i))
                .append("`")
                .append(" | +")
                .append(added)
                .append(" | ")
                .append(tokens)
                .append(" |\n");
        }

        // Per-file diffs (already annotated with [L<n>])
        for (int i = 0; i < filePaths.size(); i++) {
            summary.append("\n---\n\n### ").append(i + 1).append(". ").append(filePaths.get(i)).append("\n\n");
            summary.append("```diff\n").append(fileDiffs.get(i)).append("```\n");
        }

        byte[] summaryBytes = summary.toString().getBytes(StandardCharsets.UTF_8);
        files.put(".context/diff_summary.md", summaryBytes);
        log.info("Diff summary: {} files, {} bytes", filePaths.size(), summaryBytes.length);
    }

    /** Count lines starting with [L<n>] + (annotated added lines) in a file diff chunk. */
    private static int countAddedLines(String fileDiff) {
        int count = 0;
        for (String line : fileDiff.split("\n", -1)) {
            if (line.startsWith("[L") && line.contains("] +")) {
                count++;
            }
        }
        return count;
    }

    /** Quick-scan a file diff for notable tokens on + lines. Structural, not judgmental. */
    private static String quickScanTokens(String fileDiff) {
        Set<String> found = new LinkedHashSet<>();
        for (String line : fileDiff.split("\n", -1)) {
            if (!line.startsWith("[L") || !line.contains("] +")) continue;
            String content = line.substring(line.indexOf("] +") + 3);
            if (content.contains("try?")) found.add("try?");
            if (content.contains("fatalError")) found.add("fatalError");
            if (content.contains("!") && !content.contains("!=")) found.add("force-unwrap");
            if (content.contains("@State")) found.add("@State");
            if (content.contains("@StateObject")) found.add("@StateObject");
            if (content.contains("@Binding")) found.add("@Binding");
            if (content.contains("@Observable")) found.add("@Observable");
            if (content.contains("#Preview")) found.add("#Preview");
            if (SECURITY_KEYWORDS.matcher(content).find()) found.add("⚠secret-keyword");
            if (HIGH_ENTROPY_STRING.matcher(content).find()) found.add("⚠high-entropy");
            if (content.contains("print(")) found.add("print()");
            if (content.contains("Task {") || content.contains("Task{")) found.add("Task{}");
        }
        return found.isEmpty() ? "—" : String.join(", ", found);
    }

    /**
     * Compute valid diff note line numbers per file by parsing the full diff.
     * Used by DiffHunkValidator to snap agent-provided line numbers to valid positions.
     */
    private Map<String, TreeSet<Integer>> computeDiffValidLines(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null) return Map.of();

        long repositoryId;
        String headSha, targetBranch, sourceBranch;
        try {
            repositoryId = requireLong(metadata, "repository_id");
            headSha = requireText(metadata, "commit_sha");
            targetBranch = requireText(metadata, "target_branch");
            sourceBranch = requireText(metadata, "source_branch");
        } catch (Exception e) {
            log.debug("Cannot compute diff valid lines, missing metadata: {}", e.getMessage());
            return Map.of();
        }

        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) return Map.of();
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);

        // Get full diff using same strategy as computeAndStoreDiff
        String diff = runGit(repoPath, "diff", "origin/" + targetBranch + "..origin/" + sourceBranch);
        if (diff == null || diff.isBlank()) {
            String base = runGit(repoPath, "merge-base", "origin/" + targetBranch, headSha);
            if (base != null) {
                diff = runGit(repoPath, "diff", base.trim() + ".." + headSha);
            }
        }
        if (diff == null || diff.isBlank()) return Map.of();

        return DiffHunkValidator.parseValidLines(diff);
    }

    private Set<String> computeDiffStatFiles(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null) return Set.of();

        long repositoryId;
        String headSha, targetBranch, sourceBranch;
        try {
            repositoryId = requireLong(metadata, "repository_id");
            headSha = requireText(metadata, "commit_sha");
            targetBranch = requireText(metadata, "target_branch");
            sourceBranch = requireText(metadata, "source_branch");
        } catch (Exception e) {
            log.debug("Cannot compute diff_stat_files, missing metadata fields: {}", e.getMessage());
            return Set.of();
        }

        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) return Set.of();
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);

        // Use same strategy as computeAndStoreDiff: branch-based first, fallback to merge-base
        String diffStat = runGit(repoPath, "diff", "--stat", "origin/" + targetBranch + "..origin/" + sourceBranch);
        if (diffStat == null || diffStat.isBlank()) {
            String base = runGit(repoPath, "merge-base", "origin/" + targetBranch, headSha);
            if (base != null) {
                diffStat = runGit(repoPath, "diff", "--stat", base.trim() + ".." + headSha);
            }
        }
        if (diffStat == null || diffStat.isBlank()) return Set.of();

        return parseDiffStatPaths(diffStat);
    }

    /**
     * Parse diff stat paths, handling renames like {@code dir/{old => new}/file.ext}.
     *
     * <p>For partial renames: {@code iHabit/{HabitLogic => }/HabitAddView.swift}
     * expands to new path {@code iHabit/HabitAddView.swift}.
     */
    static Set<String> parseDiffStatPaths(String diffStat) {
        Set<String> paths = new HashSet<>();
        for (String line : diffStat.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("---") || !trimmed.contains("|")) {
                continue;
            }
            String path = trimmed.split("\\|")[0].trim();
            // Handle partial renames: "dir/{old => new}/file" -> "dir/new/file"
            if (path.contains("{") && path.contains(" => ") && path.contains("}")) {
                int braceStart = path.indexOf('{');
                int braceEnd = path.indexOf('}');
                String arrowPart = path.substring(braceStart + 1, braceEnd);
                String newPart = arrowPart.substring(arrowPart.indexOf(" => ") + 4);
                path = path.substring(0, braceStart) + newPart + path.substring(braceEnd + 1);
                // Clean up double slashes from empty rename targets
                path = path.replace("//", "/");
            } else if (path.contains(" => ")) {
                // Full rename: "old.txt => new.txt"
                path = path.substring(path.indexOf(" => ") + 4).trim();
            }
            if (!path.isEmpty()) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Filter findings to only include those whose evidence locations reference files in the diff.
     * Findings with no evidence/locations are kept (no paths to validate).
     * Findings where ALL locations are out-of-scope are removed entirely.
     */
    static List<PracticeDetectionResultParser.ValidatedFinding> filterByDiffScope(
        List<PracticeDetectionResultParser.ValidatedFinding> findings,
        Set<String> diffFiles
    ) {
        if (diffFiles.isEmpty()) return findings; // No filter if diff_stat unavailable
        List<PracticeDetectionResultParser.ValidatedFinding> filtered = new ArrayList<>();
        for (var finding : findings) {
            JsonNode evidence = finding.evidence();
            if (evidence == null || evidence.isNull() || evidence.isMissingNode()) {
                filtered.add(finding);
                continue;
            }
            JsonNode locations = evidence.get("locations");
            if (locations == null || !locations.isArray() || locations.isEmpty()) {
                filtered.add(finding);
                continue;
            }
            // Check if ANY location path is in the diff
            boolean hasInScopeLocation = false;
            for (JsonNode loc : locations) {
                JsonNode pathNode = loc.get("path");
                if (pathNode != null && diffFiles.contains(pathNode.asText())) {
                    hasInScopeLocation = true;
                    break;
                }
            }
            if (hasInScopeLocation) {
                filtered.add(finding);
            } else {
                log.info("Filtered out-of-scope finding: slug={}, paths={}", finding.practiceSlug(), locations);
            }
        }
        return filtered;
    }

    /** Run a git command in the repository directory. Returns stdout or null on failure. */
    private String runGit(Path repoPath, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoPath.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? stdout : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ObjectNode buildPullRequestMetadata(PullRequest pullRequest, JsonNode jobMetadata) {
        ObjectNode result = objectMapper.createObjectNode();

        // Copy routing fields from job metadata (always present — validated in prepareInputFiles)
        result.put("pr_number", requireInt(jobMetadata, "pr_number"));
        result.put("pr_url", requireText(jobMetadata, "pr_url"));
        result.put("repository_full_name", requireText(jobMetadata, "repository_full_name"));
        result.put("source_branch", requireText(jobMetadata, "source_branch"));
        result.put("target_branch", requireText(jobMetadata, "target_branch"));
        result.put("commit_sha", requireText(jobMetadata, "commit_sha"));

        // Enrich from current DB state (title, body, author, etc.)
        if (pullRequest == null) {
            log.warn("Pull request not found in database during context preparation");
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

    private static String requireText(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        return node.asText();
    }

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
}

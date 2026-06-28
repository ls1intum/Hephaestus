package de.tum.cit.aet.hephaestus.agent.context.providers;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireInt;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireLong;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScmTokenSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.observation.DeveloperHistoryProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises the PR-review workspace context under {@code inputs/context/}:
 * <ul>
 *   <li>{@code metadata.json} — PR metadata, enriched from DB, plus commit log</li>
 *   <li>{@code comments.json} — review comments (most recent 500)</li>
 *   <li>{@code contributor_history.json} — optional, prior findings for the author</li>
 *   <li>{@code diff.patch} — annotated unified diff via {@link GitDiffOperations}</li>
 *   <li>{@code diff_stat.txt} — {@code git diff --stat} output</li>
 *   <li>{@code diff_summary.md} — per-file diff chunks for single-pass AI consumption</li>
 * </ul>
 *
 * <p>The diff is {@link ContentProvider#required required} for this provider — an empty diff
 * aborts the build (prevents hollow positives from the agent).
 */
@Component
public class PullRequestContentProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "scm";
    }

    private static final Logger log = LoggerFactory.getLogger(PullRequestContentProvider.class);

    /** Maximum number of review comments included in context. Most recent are kept on truncation. */
    static final int MAX_COMMENTS = 500;

    /** Captures the b-side path of a git diff header — robust against renames and paths containing " b/". */
    private static final Pattern DIFF_GIT_HEADER = Pattern.compile("^diff --git a/.* b/(.+)$");

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final DeveloperHistoryProvider developerHistoryProvider;
    private final GitDiffOperations gitDiffOperations;
    private final ConnectionService connectionService;

    /**
     * SCM token sources keyed by integration kind. Collected via constructor injection so
     * adding a new SCM (Bitbucket etc.) is a matter of registering a new {@link ScmTokenSource}
     * bean — this class never has to learn the new kind.
     *
     * <p>Pre-fetch only fires when a token source is available for the workspace's active SCM
     * kind (resolved via {@link ConnectionService#findActiveProviderKind}); stale repos under
     * workspaces with no active SCM connection still get diffed against the cached clone, just
     * without a network refresh.
     */
    private final Map<IntegrationKind, ScmTokenSource> tokenSources;

    public PullRequestContentProvider(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        DeveloperHistoryProvider developerHistoryProvider,
        GitDiffOperations gitDiffOperations,
        ConnectionService connectionService,
        List<ScmTokenSource> tokenSourceList
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.developerHistoryProvider = developerHistoryProvider;
        this.gitDiffOperations = gitDiffOperations;
        this.connectionService = connectionService;
        Map<IntegrationKind, ScmTokenSource> map = new EnumMap<>(IntegrationKind.class);
        for (ScmTokenSource src : tokenSourceList) {
            map.put(src.kind(), src);
        }
        this.tokenSources = map;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        // Narrowed via supports(); cast is safe and documents the variant precondition.
        if (!(request instanceof ContextRequest.PracticeReviewRequest practiceReview)) {
            throw new IllegalStateException(
                "PullRequestContentProvider.contribute called with unsupported variant: " +
                    request.getClass().getSimpleName()
            );
        }
        AgentJob job = practiceReview.job();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        long repositoryId = requireLong(metadata, "repository_id");
        long pullRequestId = requireLong(metadata, "pull_request_id");

        ensureRepositoryAvailable(repositoryId);

        PullRequest pullRequest = pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);

        // Refresh the clone ONCE, up-front, before anything reads refs from it. The commit log
        // (addCommitLog) and the diff (computeAndStoreDiff) both resolve a range against the local
        // clone; doing the fetch here guarantees they see the same, freshest ref state. Otherwise a
        // fresh-push / stale-clone run would skip the commit log (head not yet local) while the diff,
        // computed after a later fetch, succeeds — an inconsistent context.
        String headSha = metadata.has("commit_sha") ? metadata.get("commit_sha").asString() : null;
        boolean headVerified = fetchAndVerifyHead(repositoryId, job, headSha);

        storeMetadataAndComments(files, pullRequest, pullRequestId, metadata);
        storeDeveloperHistory(files, pullRequest, job);
        computeAndStoreDiff(files, repositoryId, metadata, headVerified);
        computeAndStoreDiffSummary(files);
    }

    // Repository availability

    private void ensureRepositoryAvailable(long repositoryId) {
        if (!gitRepositoryManager.isEnabled()) {
            throw new JobPreparationException(
                "Git local checkout is disabled but required for bind-mount: repoId=" + repositoryId
            );
        }
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            throw new JobPreparationException(
                "Repository checkout is not available locally for bind-mount: repoId=" + repositoryId
            );
        }
    }

    private boolean fetchAndVerifyHead(long repositoryId, AgentJob job, String headSha) {
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            log.debug("Repository not cloned locally, skipping fetch: repoId={}", repositoryId);
            return false;
        }

        var workspace = job.getWorkspace();
        // The pre-diff fetch is gated on the workspace's active SCM kind: we resolve that
        // kind from the connection (never hardcode a vendor) and look up its token source.
        // The fetch only actually fires when that source exposes a deterministic clone URL
        // derivable from {serverUrl, repository_full_name} — see the guard below. That makes
        // it a safe no-op for kinds without such a URL (e.g. GitHub, whose source returns an
        // empty serverUrl; its historical fetches go through GithubDataSyncService instead).
        var kind =
            workspace == null
                ? Optional.<IntegrationKind>empty()
                : connectionService.findActiveProviderKind(workspace.getId());
        ScmTokenSource source = kind.map(tokenSources::get).orElse(null);

        boolean fetched = false;
        String serverUrl = null;
        try {
            if (source != null) {
                Long scopeId = workspace.getId();
                serverUrl = source.serverUrl(scopeId).orElse(null);
                String token = source.accessToken(scopeId).orElse(null);
                JsonNode metadata = job.getMetadata();
                String repoFullName =
                    metadata != null && metadata.has("repository_full_name")
                        ? metadata.get("repository_full_name").asString()
                        : null;
                if (serverUrl != null && token != null && repoFullName != null) {
                    String cloneUrl = serverUrl + "/" + repoFullName + ".git";
                    gitRepositoryManager.ensureRepository(repositoryId, cloneUrl, token);
                    fetched = true;
                    log.debug("Fetched latest refs: repoId={}", repositoryId);
                }
            }
        } catch (Exception e) {
            // Log the full exception (class + stack), not just the message: an auth/credential failure here
            // is usually systemic (it will hit every job in the workspace) and the class is what makes it
            // diagnosable. Include kind/serverUrl for triage — never the token.
            log.warn(
                "Pre-diff fetch failed: repoId={}, kind={}, serverUrl={}",
                repositoryId,
                kind.orElse(null),
                serverUrl,
                e
            );
        }

        if (headSha != null && !headSha.isBlank()) {
            boolean exists = gitRepositoryManager.commitExists(repositoryId, headSha);
            if (!exists && fetched) {
                log.error(
                    "Head commit {} not found in local clone after successful fetch. repoId={}",
                    headSha,
                    repositoryId
                );
            } else if (!exists) {
                log.warn(
                    "Head commit {} not found locally (no fetch possible). Diff may fail. repoId={}",
                    headSha,
                    repositoryId
                );
            }
            return exists;
        }
        return false;
    }

    // Metadata + comments

    private void storeMetadataAndComments(
        Map<String, byte[]> files,
        @Nullable PullRequest pullRequest,
        long pullRequestId,
        JsonNode metadata
    ) {
        ObjectNode pullRequestMetadata = buildPullRequestMetadata(pullRequest, metadata);
        addCommitLog(pullRequestMetadata, metadata);
        try {
            files.put(
                OUTPUT_PREFIX + "metadata.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pullRequestMetadata)
            );
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize pull request metadata", e);
        }

        JsonNode comments = buildReviewComments(pullRequestId);
        try {
            files.put(
                OUTPUT_PREFIX + "comments.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comments)
            );
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize review comments", e);
        }
    }

    private ObjectNode buildPullRequestMetadata(@Nullable PullRequest pullRequest, JsonNode jobMetadata) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("pr_number", requireInt(jobMetadata, "pr_number"));
        result.put("pr_url", requireText(jobMetadata, "pr_url"));
        result.put("repository_full_name", requireText(jobMetadata, "repository_full_name"));
        result.put("source_branch", requireText(jobMetadata, "source_branch"));
        result.put("target_branch", requireText(jobMetadata, "target_branch"));
        result.put("commit_sha", requireText(jobMetadata, "commit_sha"));

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
            // line is a primitive int; a file-level / general review comment has no anchored line and reports 0.
            // Omit the key in that case so an absent anchor reads as absent, not as a literal line-0 anchor.
            if (comment.getLine() > 0) {
                commentNode.put("line", comment.getLine());
            }
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

    private void addCommitLog(ObjectNode metadata, JsonNode jobMetadata) {
        String sourceBranch = jobMetadata.has("source_branch") ? jobMetadata.get("source_branch").asString() : null;
        String targetBranch = jobMetadata.has("target_branch") ? jobMetadata.get("target_branch").asString() : null;
        long repositoryId = requireLong(jobMetadata, "repository_id");

        if (sourceBranch == null || targetBranch == null) return;

        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
        String headSha = jobMetadata.has("commit_sha") ? jobMetadata.get("commit_sha").asString() : null;

        String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
        String logOutput = range != null ? gitDiffOperations.shortLog(repoPath, range[0], range[1]) : null;

        if (logOutput == null || logOutput.isBlank()) {
            log.debug("No commit log available for MR, skipping commit injection");
            return;
        }

        ArrayNode commits = objectMapper.createArrayNode();
        int count = 0;
        for (String line : logOutput.split("\n")) {
            if (line.isBlank()) continue;
            if (count >= 50) break;
            String[] parts = line.split("\t", 2);
            if (parts.length < 2) continue;
            ObjectNode commit = objectMapper.createObjectNode();
            commit.put("sha", parts[0]);
            commit.put("message", parts[1]);
            commits.add(commit);
            count++;
        }

        if (!commits.isEmpty()) {
            metadata.set("commits", commits);
            log.debug("Injected {} commit messages into metadata", commits.size());
        }
    }

    // Developer history

    private void storeDeveloperHistory(Map<String, byte[]> files, @Nullable PullRequest pullRequest, AgentJob job) {
        if (pullRequest == null || pullRequest.getAuthor() == null || job.getWorkspace() == null) {
            if (pullRequest != null && pullRequest.getAuthor() == null) {
                log.debug("Skipping developer history: PR has no author, pullRequestId={}", pullRequest.getId());
            }
            return;
        }
        Long developerId = pullRequest.getAuthor().getId();
        Long workspaceId = job.getWorkspace().getId();

        try {
            Optional<byte[]> historyJson = developerHistoryProvider.buildHistoryJson(developerId, workspaceId);
            historyJson.ifPresent(json -> {
                files.put(OUTPUT_PREFIX + "contributor_history.json", json);
                log.info(
                    "Injected developer history: {} bytes, developerId={}, workspaceId={}",
                    json.length,
                    developerId,
                    workspaceId
                );
            });
        } catch (Exception e) {
            log.warn(
                "Failed to build developer history, continuing without it: developerId={}, workspaceId={}",
                developerId,
                workspaceId,
                e
            );
        }
    }

    // Diff

    private void computeAndStoreDiff(
        Map<String, byte[]> files,
        long repositoryId,
        JsonNode metadata,
        boolean headVerified
    ) {
        String headSha = metadata.has("commit_sha") ? metadata.get("commit_sha").asString() : null;
        String targetBranch = requireText(metadata, "target_branch");
        String sourceBranch = requireText(metadata, "source_branch");
        if (headSha == null || headSha.isBlank()) {
            log.warn("No commit_sha in metadata, skipping diff pre-computation");
            return;
        }
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);

        try {
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null) {
                if (headVerified) {
                    throw new JobPreparationException(
                        "Cannot compute diff: all resolution strategies failed despite head commit " +
                            headSha +
                            " being present. targetBranch=" +
                            targetBranch +
                            ", sourceBranch=" +
                            sourceBranch +
                            ", repoId=" +
                            repositoryId
                    );
                }
                log.error(
                    "Cannot compute diff: head commit not available locally. " +
                        "headSha={}, targetBranch={}, sourceBranch={}, repoId={}",
                    headSha,
                    targetBranch,
                    sourceBranch,
                    repositoryId
                );
                return;
            }
            String diffStat = gitDiffOperations.diffStat(repoPath, range[0], range[1]);
            String diff = gitDiffOperations.diff(repoPath, range[0], range[1]);
            if (diff != null && !diff.isBlank()) {
                String annotatedDiff = GitDiffOperations.annotateDiffWithLineNumbers(diff);
                files.put(OUTPUT_PREFIX + "diff.patch", annotatedDiff.getBytes(StandardCharsets.UTF_8));
                if (diffStat != null) {
                    files.put(OUTPUT_PREFIX + "diff_stat.txt", diffStat.getBytes(StandardCharsets.UTF_8));
                }

                int addedLines = 0;
                int removedLines = 0;
                for (String line : diff.split("\n", -1)) {
                    if (line.startsWith("+") && !line.startsWith("+++")) addedLines++;
                    else if (line.startsWith("-") && !line.startsWith("---")) removedLines++;
                }
                String strategyUsed = range[1].equals(headSha) ? "SHA-based" : "branch-based";
                log.info(
                    "Pre-computed diff: strategy={}, range={}..{}, +{}/-{} lines, {} bytes (annotated: {} bytes), headSha={}",
                    strategyUsed,
                    range[0],
                    range[1],
                    addedLines,
                    removedLines,
                    diff.length(),
                    annotatedDiff.length(),
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

    /** Pure transformation: build the per-file diff summary from {@code diff.patch}. */
    void computeAndStoreDiffSummary(Map<String, byte[]> files) {
        byte[] diffBytes = files.get(OUTPUT_PREFIX + "diff.patch");
        if (diffBytes == null || diffBytes.length == 0) {
            return;
        }

        String diff = new String(diffBytes, StandardCharsets.UTF_8);

        List<String> fileDiffs = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String currentPath = null;

        for (String line : diff.split("\n", -1)) {
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
                Matcher m = DIFF_GIT_HEADER.matcher(effectiveLine);
                // On a malformed/unusual header the captured path is unavailable; fall back to the raw
                // header line, but sanitize it first — it is rendered verbatim into a markdown table cell
                // and a section heading, so a stray pipe/backtick would corrupt the table the agent reads.
                currentPath = m.matches() ? m.group(1) : sanitizePathCell(effectiveLine);
            }
            currentChunk.append(line).append('\n');
        }
        if (currentPath != null) {
            fileDiffs.add(currentChunk.toString());
            filePaths.add(currentPath);
        }

        StringBuilder summary = new StringBuilder();
        summary.append("# Diff Summary\n\n");
        summary.append("**").append(filePaths.size()).append(" files changed**\n\n");

        summary.append("| # | File | +Lines |\n");
        summary.append("|---|------|--------|\n");
        for (int i = 0; i < filePaths.size(); i++) {
            int added = countAddedLines(fileDiffs.get(i));
            summary
                .append("| ")
                .append(i + 1)
                .append(" | `")
                .append(filePaths.get(i))
                .append("` | +")
                .append(added)
                .append(" |\n");
        }

        for (int i = 0; i < filePaths.size(); i++) {
            summary.append("\n---\n\n### ").append(i + 1).append(". ").append(filePaths.get(i)).append("\n\n");
            summary.append("```diff\n").append(fileDiffs.get(i)).append("```\n");
        }

        byte[] summaryBytes = summary.toString().getBytes(StandardCharsets.UTF_8);
        files.put(OUTPUT_PREFIX + "diff_summary.md", summaryBytes);
        log.info("Diff summary: {} files, {} bytes", filePaths.size(), summaryBytes.length);
    }

    /**
     * Sanitizes a fallback (unparseable) diff-header path before it is placed into the markdown summary's
     * table cell and section heading: strips the {@code diff --git } prefix and removes pipe/backtick
     * characters that would break the markdown table the agent consumes.
     */
    private static String sanitizePathCell(String rawHeader) {
        String stripped = rawHeader.startsWith("diff --git ") ? rawHeader.substring("diff --git ".length()) : rawHeader;
        return stripped.replace("|", "").replace("`", "").trim();
    }

    private static int countAddedLines(String fileDiff) {
        int count = 0;
        for (String line : fileDiff.split("\n", -1)) {
            if (line.startsWith("[L") && line.contains("] +")) {
                count++;
            }
        }
        return count;
    }
}

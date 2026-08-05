package de.tum.cit.aet.hephaestus.agent.context.providers;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireInt;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireLong;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScmTokenSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Order(100)
public class PullRequestContentSource implements EvidenceSource {

    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind COMMENTS = new SourceKind("scm.pull-request.comments");

    @Override
    public Set<SourceKind> sourceKinds() {
        return Set.of(CORE, DIFF, COMMENTS);
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        if (path.endsWith("comments.json")) return COMMENTS;
        if (
            path.endsWith("diff.patch") || path.endsWith("diff_stat.txt") || path.endsWith("diff_summary.md")
        ) return DIFF;
        return CORE;
    }

    private static final Logger log = LoggerFactory.getLogger(PullRequestContentSource.class);

    static final int MAX_COMMENTS = 500;

    /** Captures the b-side path of a git diff header — robust against renames and paths containing " b/". */
    private static final Pattern DIFF_GIT_HEADER = Pattern.compile("^diff --git a/.* b/(.+)$");

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final GitDiffOperations gitDiffOperations;
    private final ConnectionService connectionService;

    private final Map<IntegrationKind, ScmTokenSource> tokenSources;

    public PullRequestContentSource(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        GitDiffOperations gitDiffOperations,
        ConnectionService connectionService,
        List<ScmTokenSource> tokenSourceList
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewCommentRepository = reviewCommentRepository;
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
        prepareCapture(request, sourceKinds());
        contributeSelected(request, sourceKinds(), files);
    }

    @Override
    public void prepareCapture(ContextRequest request, Set<SourceKind> selectedKinds) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest practiceReview)) return;
        if (!selectedKinds.contains(DIFF)) return;
        JsonNode metadata = practiceReview.job().getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) return;
        long repositoryId = requireLong(metadata, "repository_id");
        if (!gitRepositoryManager.isEnabled() || !gitRepositoryManager.isRepositoryCloned(repositoryId)) return;
        String headSha = metadata.path("commit_sha").asString(null);
        fetchAndVerifyHead(repositoryId, practiceReview.job(), headSha);
    }

    @Override
    public void contributeSelected(ContextRequest request, Set<SourceKind> selectedKinds, Map<String, byte[]> files) {
        files.putAll(captureSelected(request, selectedKinds).files());
    }

    @Override
    public EvidenceContribution capture(ContextRequest request, Set<SourceKind> selectedKinds) {
        return captureSelected(request, selectedKinds);
    }

    private EvidenceContribution captureSelected(ContextRequest request, Set<SourceKind> selectedKinds) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest practiceReview)) {
            throw new IllegalStateException(
                "PullRequestContentSource.contribute called with unsupported variant: " +
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
        Map<String, byte[]> files = new HashMap<>();
        Map<SourceKind, SourceCompleteness> completeness = new HashMap<>();
        Map<SourceKind, String> identities = new HashMap<>();
        Map<SourceKind, java.time.Instant> observedAt = new HashMap<>();
        Map<SourceKind, SourceContentState> contentStates = new HashMap<>();

        boolean headVerified = false;
        if (selectedKinds.contains(DIFF)) {
            ensureRepositoryAvailable(repositoryId);
            String headSha = metadata.has("commit_sha") ? metadata.get("commit_sha").asString() : null;
            headVerified =
                headSha != null && !headSha.isBlank() && gitRepositoryManager.commitExists(repositoryId, headSha);
        }
        if (selectedKinds.contains(CORE)) {
            PullRequest pullRequest = pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);
            storeMetadata(files, pullRequest, metadata);
            completeness.put(CORE, pullRequest == null ? SourceCompleteness.PARTIAL : SourceCompleteness.COMPLETE);
            if (pullRequest != null && pullRequest.getLastSyncAt() != null) {
                observedAt.put(CORE, pullRequest.getLastSyncAt());
            }
        }
        if (selectedKinds.contains(COMMENTS)) {
            CommentCapture comments = loadComments(pullRequestId);
            storeComments(files, comments.comments());
            completeness.put(COMMENTS, comments.complete() ? SourceCompleteness.COMPLETE : SourceCompleteness.PARTIAL);
            contentStates.put(
                COMMENTS,
                comments.comments().isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
            );
        }
        if (selectedKinds.contains(DIFF)) {
            computeAndStoreDiff(files, repositoryId, metadata, headVerified);
            computeAndStoreDiffSummary(files);
            completeness.put(DIFF, SourceCompleteness.COMPLETE);
            String headSha = metadata.path("commit_sha").asString();
            if (!headSha.isBlank()) identities.put(DIFF, headSha);
            byte[] diff = files.get(OUTPUT_PREFIX + "diff.patch");
            contentStates.put(
                DIFF,
                diff == null || diff.length == 0 ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
            );
        }
        return new EvidenceContribution(files, completeness, identities, observedAt, Map.of(), contentStates);
    }

    private void ensureRepositoryAvailable(long repositoryId) {
        if (!gitRepositoryManager.isEnabled()) {
            throw new JobPreparationException(
                "Git local storage is disabled but required for repository evidence: repoId=" + repositoryId
            );
        }
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            throw new JobPreparationException(
                "Repository is not available locally for evidence capture: repoId=" + repositoryId
            );
        }
    }

    private boolean fetchAndVerifyHead(long repositoryId, AgentJob job, String headSha) {
        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            log.debug("Repository not cloned locally, skipping fetch: repoId={}", repositoryId);
            return false;
        }

        var workspace = job.getWorkspace();
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
                    long pullRequestNumber = requireLong(metadata, "pr_number");
                    Optional<String> reviewHeadRef = source.reviewHeadRef(pullRequestNumber);
                    if (headSha != null && !headSha.isBlank() && reviewHeadRef.isPresent()) {
                        boolean pinnedHeadFetched = gitRepositoryManager.fetchRemoteCommit(
                            repositoryId,
                            reviewHeadRef.get(),
                            headSha,
                            token
                        );
                        if (!pinnedHeadFetched) {
                            log.warn("Remote review ref did not match the pinned commit: repoId={}", repositoryId);
                        }
                    }
                    log.debug("Fetched latest refs: repoId={}", repositoryId);
                }
            }
        } catch (Exception e) {
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

    private void storeMetadata(Map<String, byte[]> files, @Nullable PullRequest pullRequest, JsonNode metadata) {
        ObjectNode pullRequestMetadata = buildPullRequestMetadata(pullRequest, metadata);
        try {
            files.put(
                OUTPUT_PREFIX + "metadata.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pullRequestMetadata)
            );
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize pull request metadata", e);
        }
    }

    private void storeComments(Map<String, byte[]> files, List<PullRequestReviewComment> comments) {
        JsonNode serialized = buildReviewComments(comments);
        try {
            files.put(
                OUTPUT_PREFIX + "comments.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(serialized)
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

    private CommentCapture loadComments(long pullRequestId) {
        var comments = new ArrayList<>(
            reviewCommentRepository.findRecentByPullRequestIdWithAuthor(
                pullRequestId,
                PageRequest.of(0, MAX_COMMENTS + 1)
            )
        );
        if (comments.size() > MAX_COMMENTS + 1) {
            comments = new ArrayList<>(comments.subList(0, MAX_COMMENTS + 1));
        }
        boolean complete = comments.size() <= MAX_COMMENTS;
        if (!complete) comments.remove(comments.size() - 1);
        comments.sort(
            Comparator.comparing(
                PullRequestReviewComment::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
        );
        return new CommentCapture(comments, complete);
    }

    private JsonNode buildReviewComments(List<PullRequestReviewComment> comments) {
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

    private record CommentCapture(List<PullRequestReviewComment> comments, boolean complete) {}

    private void computeAndStoreDiff(
        Map<String, byte[]> files,
        long repositoryId,
        JsonNode metadata,
        boolean headVerified
    ) {
        String headSha = metadata.has("commit_sha") ? metadata.get("commit_sha").asString() : null;
        String targetBranch = requireText(metadata, "target_branch");
        String sourceBranch = requireText(metadata, "source_branch");
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);

        try {
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null) {
                String reason = headVerified
                    ? "all resolution strategies failed"
                    : "the pinned head commit is unavailable after repository refresh";
                throw new JobPreparationException(
                    "Cannot compute diff because " +
                        reason +
                        ". headSha=" +
                        headSha +
                        ", targetBranch=" +
                        targetBranch +
                        ", sourceBranch=" +
                        sourceBranch +
                        ", repoId=" +
                        repositoryId
                );
            }
            String diffStat = gitDiffOperations.diffStat(repoPath, range[0], range[1]);
            String diff = gitDiffOperations.diff(repoPath, range[0], range[1]);
            // A null diff denotes a failed read: an unresolved object, an I/O error, or the size
            // cap. It must not be stored as an empty diff. The contract does not permit PARTIAL for
            // this source, so an unreadable diff is reported as a collection error; storing zero
            // bytes would report AVAILABLE, EMPTY and COMPLETE for a change that was never read.
            if (diff == null) {
                throw new JobPreparationException(
                    "Diff could not be read for range=" +
                        range[0] +
                        ".." +
                        range[1] +
                        ", headSha=" +
                        headSha +
                        ", repoId=" +
                        repositoryId
                );
            }
            if (!diff.isBlank()) {
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
                files.put(OUTPUT_PREFIX + "diff.patch", new byte[0]);
                files.put(OUTPUT_PREFIX + "diff_stat.txt", new byte[0]);
                log.info("Pre-computed empty diff: range={}..{}, headSha={}", range[0], range[1], headSha);
            }
        } catch (JobPreparationException e) {
            throw e;
        } catch (Exception e) {
            throw new JobPreparationException("Failed to pre-compute diff: " + e.getMessage(), e);
        }
    }

    void computeAndStoreDiffSummary(Map<String, byte[]> files) {
        byte[] diffBytes = files.get(OUTPUT_PREFIX + "diff.patch");
        if (diffBytes == null) {
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
                // A malformed header remains attacker-controlled; escape it before rendering Markdown.
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

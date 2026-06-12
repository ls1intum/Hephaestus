package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations;
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
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Handler for {@link AgentJobType#PULL_REQUEST_REVIEW} jobs.
 *
 * <p>Delegates workspace-context materialisation to {@link WorkspaceContextBuilder} (which
 * orchestrates {@code PullRequestContentProvider} → {@code context/target/...} files) and the
 * task envelope to {@link TaskEnvelopeWriter}. Retains practice catalog injection ({@code .practices/})
 * and delivery-phase post-processing here — catalog injection is per-job and not provider-shaped.
 *
 * <p>Container workspace layout (see {@code resources/agent/WORKSPACE_ABI.md} for the full ABI):
 * <pre>
 * /workspace/
 * ├── repo/                              # git repo (read-only bind mount)
 * ├── context/target/                    # workspace context (this handler populates via WorkspaceContextBuilder)
 * │   ├── metadata.json                  #   PR metadata + commits
 * │   ├── comments.json                  #   review comments
 * │   ├── diff.patch                     #   diff with [L&lt;n&gt;] annotations
 * │   ├── diff_stat.txt                  #   changed files
 * │   ├── diff_summary.md                #   per-file diff chunks
 * │   └── contributor_history.json       #   prior findings (optional)
 * ├── task.json                          # Task envelope (TaskEnvelope around Task.PracticeReview)
 * ├── .practices/{index.json, {slug}.md, all-criteria.md}
 * ├── .precompute/practices/{slug}.ts
 * ├── .precompute-out/
 * ├── .pi/{AGENTS.md, settings.json, extensions/} # Pi SDK agent dir ($PI_CODING_AGENT_DIR)
 * ├── .run-pi.mjs                          # runner entry point
 * └── .output/
 * </pre>
 */
public class PullRequestReviewHandler implements JobTypeHandler {

    /**
     * Materialized context files a finding may legitimately cite that survive the post-agent
     * {@code filterByDiffScope} pass. metadata.json carries the PR fields; diff.patch / diff_summary.md
     * ARE the change under review (so a finding anchored there is in-scope by definition — the
     * code-judging practices quote a {@code [L<n>]} span in diff.patch); comments.json is the review
     * thread the reviewer-side practices read. These resolve as internal paths in DeliveryComposer, so
     * such findings render as non-inlinable summary items rather than diff-anchored inline notes.
     */
    private static final Set<String> ALLOWED_INTERNAL_CONTEXT_PATHS = Set.of(
        ContentProvider.OUTPUT_PREFIX + "metadata.json",
        ContentProvider.OUTPUT_PREFIX + "diff.patch",
        ContentProvider.OUTPUT_PREFIX + "diff_summary.md",
        ContentProvider.OUTPUT_PREFIX + "comments.json"
    );

    /**
     * Process/metadata-level PR practices whose evidence is the PR metadata, the commit subjects, or the
     * review thread — NOT a diff line. {@code filterByDiffScope} is a guard for CODE-defect findings whose
     * location must sit inside the diff; applied to these process practices it would wrongly drop a valid
     * finding the moment the agent attaches a stray (non-diff) location to it (observed: a commit-subject
     * finding citing a commit ref). These slugs therefore bypass the diff-scope filter.
     */
    private static final Set<String> METADATA_LEVEL_PRACTICES = Set.of(
        "scope-one-reviewable-change",
        "describe-what-and-why",
        "ready-and-traceable-handoff",
        "commit-subjects-explain-each-change",
        "engaging-with-inline-review-comments",
        "mr-description-quality",
        "commit-discipline"
    );

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewHandler.class);

    private final JsonMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PracticeCatalogInjector practiceCatalogInjector;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final GitDiffOperations gitDiffOperations;
    private final PracticeDetectionResultParser resultParser;
    private final PracticeDetectionDeliveryService deliveryService;
    private final FeedbackDeliveryService feedbackService;
    private final SecretDiffScanner secretDiffScanner;

    PullRequestReviewHandler(
        JsonMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PracticeCatalogInjector practiceCatalogInjector,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter,
        GitDiffOperations gitDiffOperations,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService,
        SecretDiffScanner secretDiffScanner
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.practiceCatalogInjector = practiceCatalogInjector;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
        this.gitDiffOperations = gitDiffOperations;
        this.resultParser = resultParser;
        this.deliveryService = deliveryService;
        this.feedbackService = feedbackService;
        this.secretDiffScanner = secretDiffScanner;
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

        ScmEventPayload.PullRequestData pullRequestData = submissionRequest.pullRequest();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", pullRequestData.repository().id());
        metadata.put("repository_full_name", pullRequestData.repository().nameWithOwner());
        metadata.put("pull_request_id", pullRequestData.id());
        metadata.put("pr_number", pullRequestData.number());
        metadata.put("pr_url", pullRequestData.htmlUrl());
        metadata.put("commit_sha", submissionRequest.headRefOid());
        metadata.put("source_branch", submissionRequest.headRefName());
        metadata.put("target_branch", submissionRequest.baseRefName());
        // The MR title + description are the sole inputs for the communication/process practices
        // (mr-description-quality, commit-discipline) — their precompute scripts read metadata.title /
        // metadata.body. Without these the practices silently can't evaluate.
        metadata.put("title", pullRequestData.title());
        metadata.put("body", pullRequestData.body());

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
     * Prepare context files for the agent. Workspace context is materialised by
     * {@link WorkspaceContextBuilder}; task envelope by {@link TaskEnvelopeWriter}; practice
     * catalog stays here (per-job, not provider-shaped).
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

        // LinkedHashMap: deterministic iteration order for snapshot fixtures.
        Map<String, byte[]> files = new LinkedHashMap<>(
            workspaceContextBuilder.build(new ContextRequest.PracticeReviewRequest(job))
        );

        // Task envelope replaces the legacy /workspace/.prompt file.
        files.put(WorkspaceAbi.TASK_ENVELOPE_FILENAME, taskEnvelopeWriter.write(buildTaskEnvelope(job, metadata)));

        practiceCatalogInjector.inject(files, job, WorkArtifact.PULL_REQUEST);

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

    private TaskEnvelope buildTaskEnvelope(AgentJob job, JsonNode metadata) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Task task = new Task.PracticeReview(
            buildPrompt(job),
            requireInt(metadata, "pr_number"),
            requireText(metadata, "repository_full_name")
        );
        return TaskEnvelope.of(job.getId(), job.getWorkspace().getId(), task);
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
        return Map.of(repoPath.toAbsolutePath().toString(), WorkspaceAbi.REPO_MOUNT);
    }

    private String buildPrompt(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        int pullRequestNumber = requireInt(metadata, "pr_number");
        String repoName = requireText(metadata, "repository_full_name");

        String prompt =
            "Review merge request #" +
            pullRequestNumber +
            " in " +
            repoName +
            ". Read the context files, then persist every justified finding via the report_finding tool. " +
            "Follow " +
            WorkspaceAbi.ORCHESTRATOR_PATH +
            " for the schema and rules.";
        log.info("Built orchestrator prompt: {} chars, jobId={}", prompt.length(), job.getId());
        return prompt;
    }

    // Delivery

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

        // Deterministic, LLM-independent secret pre-pass over the raw diff. This catches a committed
        // credential even when the model abstains, the precompute crashes, or the clone is checked
        // out at the merge base (so a working-tree grep finds nothing). The synthetic NEGATIVE
        // findings flow through the normal persist+compose+deliver path and force the green→red flip.
        List<PracticeDetectionResultParser.ValidatedFinding> secretFindings = scanForSecrets(
            job,
            parsed.validFindings()
        );

        boolean allNotApplicable = parsed
            .validFindings()
            .stream()
            .allMatch(f -> f.verdict() == Verdict.NOT_APPLICABLE);
        if (allNotApplicable && secretFindings.isEmpty()) {
            Set<String> diffFiles = computeDiffStatFiles(job);
            boolean hasDiffContent = !diffFiles.isEmpty();
            if (hasDiffContent) {
                throw new JobDeliveryException(
                    "All findings are NOT_APPLICABLE but the diff contains " +
                        diffFiles.size() +
                        " files — likely a stale/empty diff was provided to the agent. " +
                        "Refusing to deliver. jobId=" +
                        job.getId()
                );
            }
        }

        Set<String> diffFiles = computeDiffStatFiles(job);
        var scopedFindings = new ArrayList<>(filterByDiffScope(parsed.validFindings(), diffFiles));
        if (scopedFindings.size() < parsed.validFindings().size()) {
            log.info(
                "Diff scope filter removed {} out-of-scope findings: jobId={}, before={}, after={}",
                parsed.validFindings().size() - scopedFindings.size(),
                job.getId(),
                parsed.validFindings().size(),
                scopedFindings.size()
            );
        }
        // Secret findings are inherently in-diff (their location is an added line) — inject AFTER the
        // diff-scope filter so a path-normalisation mismatch can never silently drop a credential.
        if (!secretFindings.isEmpty()) {
            scopedFindings.addAll(secretFindings);
            log.warn(
                "Secret pre-pass injected {} hardcoded-secrets NEGATIVE finding(s); blocking any all-clear comment: jobId={}",
                secretFindings.size(),
                job.getId()
            );
        }
        if (scopedFindings.isEmpty()) {
            throw new JobDeliveryException(
                "All findings were filtered by diff scope: jobId=" +
                    job.getId() +
                    ", before=" +
                    parsed.validFindings().size() +
                    ", diffFiles=" +
                    diffFiles.size()
            );
        }

        PracticeDetectionDeliveryService.DeliveryResult result;
        try {
            result = deliveryService.deliver(job, scopedFindings);
            log.info(
                "Delivery complete: inserted={}, unknownSlug={}, duplicate={}, jobId={}",
                result.inserted(),
                result.discardedUnknownSlug(),
                result.discardedDuplicate(),
                job.getId()
            );
        } catch (JobDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new JobDeliveryException("Delivery failed unexpectedly: jobId=" + job.getId(), e);
        }

        PracticeDetectionResultParser.DeliveryContent delivery = DeliveryComposer.compose(scopedFindings);
        if (delivery != null) {
            log.info("Server-side delivery composed from {} findings: jobId={}", scopedFindings.size(), job.getId());
            if (!delivery.diffNotes().isEmpty()) {
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
        }

        feedbackService.deliverFeedback(job, delivery);
    }

    // Delivery-phase diff helpers (use GitDiffOperations; no longer duplicated in the handler)

    /**
     * Run the deterministic secret pre-pass over the job's raw diff and map each hit to a synthetic
     * {@code hardcoded-secrets} NEGATIVE finding. Hits already covered by an LLM-produced
     * hardcoded-secrets finding (same matched token already quoted) are skipped to avoid double-posting.
     */
    private List<PracticeDetectionResultParser.ValidatedFinding> scanForSecrets(
        AgentJob job,
        List<PracticeDetectionResultParser.ValidatedFinding> existing
    ) {
        String diff = computeUnifiedDiff(job);
        List<SecretDiffScanner.SecretHit> hits = secretDiffScanner.scan(diff);
        if (hits.isEmpty()) return List.of();

        // De-dup synthetic hits against credentials an LLM finding already flagged, keyed by IN-DIFF
        // POSITION (path:line). Position is stable regardless of how the LLM worded its evidence or which
        // canonical token the scanner emitted — e.g. the scanner's "-----BEGIN PRIVATE KEY-----" token
        // never appears verbatim when the line is the "-----BEGIN RSA PRIVATE KEY-----" variant, so a
        // token-substring check alone would re-post the same secret. The token check stays only as a
        // fallback for LLM findings that carry no parseable location.
        Set<String> llmPositions = new HashSet<>();
        Set<String> llmQuoted = new HashSet<>();
        for (var f : existing) {
            if (
                !"hardcoded-secrets".equals(f.practiceSlug()) || f.verdict() != Verdict.NEGATIVE || f.evidence() == null
            ) {
                continue;
            }
            boolean hadLocation = false;
            JsonNode locations = f.evidence().get("locations");
            if (locations != null && locations.isArray()) {
                for (JsonNode loc : locations) {
                    JsonNode pathNode = loc.get("path");
                    JsonNode lineNode = loc.get("startLine");
                    if (pathNode != null && !pathNode.isNull() && lineNode != null && lineNode.isNumber()) {
                        llmPositions.add(pathNode.asString() + ":" + lineNode.asInt());
                        hadLocation = true;
                    }
                }
            }
            if (!hadLocation) {
                llmQuoted.add(f.evidence().toString());
            }
        }

        List<PracticeDetectionResultParser.ValidatedFinding> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SecretDiffScanner.SecretHit hit : hits) {
            String key = hit.path() + ":" + hit.newLine() + ":" + hit.ruleId();
            if (!seen.add(key)) continue;
            if (llmPositions.contains(hit.path() + ":" + hit.newLine())) continue;
            if (llmQuoted.stream().anyMatch(q -> q.contains(hit.matchedToken()))) continue;
            out.add(toSecretFinding(hit));
        }
        return out;
    }

    private PracticeDetectionResultParser.ValidatedFinding toSecretFinding(SecretDiffScanner.SecretHit hit) {
        ObjectNode evidence = objectMapper.createObjectNode();
        ArrayNode locations = evidence.putArray("locations");
        ObjectNode location = locations.addObject();
        location.put("path", hit.path());
        location.put("startLine", hit.newLine());
        ArrayNode snippets = evidence.putArray("snippets");
        snippets.add(hit.addedLine());

        boolean lowSignal = secretDiffScanner.isLowSignalPath(hit.path());
        de.tum.cit.aet.hephaestus.practices.model.Severity severity = (hit.isCritical() && !lowSignal)
            ? de.tum.cit.aet.hephaestus.practices.model.Severity.CRITICAL
            : de.tum.cit.aet.hephaestus.practices.model.Severity.MAJOR;

        String reasoning =
            "A credential appears on a changed line: `" +
            hit.addedLine() +
            "`. Committed secrets remain in the git history permanently — even after the line is removed — so the key must be treated as compromised.";
        String guidance =
            "Remove the literal value, rotate the credential immediately, and load it at runtime from an environment variable or a secrets manager instead of hardcoding it.";

        return new PracticeDetectionResultParser.ValidatedFinding(
            "hardcoded-secrets",
            "Hardcoded secret on a changed line",
            Verdict.NEGATIVE,
            severity,
            1.0f,
            evidence,
            reasoning,
            guidance,
            List.of()
        );
    }

    /** Raw (un-annotated) unified diff for the job's PR range, or null when unavailable. */
    private String computeUnifiedDiff(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null) return null;

        long repositoryId;
        String headSha, targetBranch, sourceBranch;
        try {
            repositoryId = requireLong(metadata, "repository_id");
            headSha = requireText(metadata, "commit_sha");
            targetBranch = requireText(metadata, "target_branch");
            sourceBranch = requireText(metadata, "source_branch");
        } catch (Exception e) {
            log.debug("Cannot compute unified diff, missing metadata: {}", e.getMessage());
            return null;
        }

        if (!gitRepositoryManager.isRepositoryCloned(repositoryId)) return null;
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);

        String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
        if (range == null) return null;

        String diff = gitDiffOperations.diff(repoPath, range[0], range[1]);
        return (diff == null || diff.isBlank()) ? null : diff;
    }

    private Map<String, TreeSet<Integer>> computeDiffValidLines(AgentJob job) {
        String diff = computeUnifiedDiff(job);
        if (diff == null) return Map.of();
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

        String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
        if (range == null) return Set.of();

        String nameOnly = gitDiffOperations.diffNameOnly(repoPath, range[0], range[1]);
        if (nameOnly == null || nameOnly.isBlank()) return Set.of();

        return parseDiffNameOnlyPaths(nameOnly);
    }

    /**
     * Parse file paths from {@code git diff --name-only} output.
     * Each non-blank line is a file path — no truncation or stat formatting.
     */
    static Set<String> parseDiffNameOnlyPaths(String nameOnlyOutput) {
        Set<String> paths = new HashSet<>();
        for (String line : nameOnlyOutput.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    /**
     * Parse diff stat paths, handling renames like {@code dir/{old => new}/file.ext}.
     */
    static Set<String> parseDiffStatPaths(String diffStat) {
        Set<String> paths = new HashSet<>();
        for (String line : diffStat.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("---") || !trimmed.contains("|")) {
                continue;
            }
            String path = trimmed.split("\\|")[0].trim();
            if (path.contains("{") && path.contains(" => ") && path.contains("}")) {
                int braceStart = path.indexOf('{');
                int braceEnd = path.indexOf('}');
                String arrowPart = path.substring(braceStart + 1, braceEnd);
                String newPart = arrowPart.substring(arrowPart.indexOf(" => ") + 4);
                path = path.substring(0, braceStart) + newPart + path.substring(braceEnd + 1);
                path = path.replace("//", "/");
            } else if (path.contains(" => ")) {
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
     */
    static List<PracticeDetectionResultParser.ValidatedFinding> filterByDiffScope(
        List<PracticeDetectionResultParser.ValidatedFinding> findings,
        Set<String> diffFiles
    ) {
        if (diffFiles.isEmpty()) return findings;
        List<PracticeDetectionResultParser.ValidatedFinding> filtered = new ArrayList<>();
        for (var finding : findings) {
            // Process/metadata-level practices are not diff-anchored — never drop them on a location mismatch.
            if (METADATA_LEVEL_PRACTICES.contains(finding.practiceSlug())) {
                filtered.add(finding);
                continue;
            }
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
            boolean hasInScopeLocation = false;
            for (JsonNode loc : locations) {
                JsonNode pathNode = loc.get("path");
                if (pathNode == null || pathNode.isNull() || pathNode.isMissingNode()) {
                    continue;
                }
                String path = pathNode.asString();
                if (path.isBlank() || "null".equals(path)) {
                    continue;
                }
                // The agent cites files it read under the repo mount as "repo/<path>", but diff-stat
                // paths are repo-relative ("<path>"). Strip the mount prefix so a code finding on a
                // genuinely-changed file is not dropped on a cosmetic path mismatch.
                String repoRelative = path.startsWith("repo/") ? path.substring("repo/".length()) : path;
                if (diffFiles.contains(path) || diffFiles.contains(repoRelative) || isInternalContextPath(path)) {
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

    private static boolean isInternalContextPath(String path) {
        return ALLOWED_INTERNAL_CONTEXT_PATHS.contains(path);
    }

    // Metadata field helpers

    private static String requireText(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        return node.asString();
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

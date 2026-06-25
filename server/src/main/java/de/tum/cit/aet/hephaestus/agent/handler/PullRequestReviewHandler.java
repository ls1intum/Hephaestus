package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireInt;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireLong;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

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
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
 * orchestrates {@code PullRequestContentProvider} → {@code inputs/context/...} files) and the
 * task envelope to {@link TaskEnvelopeWriter}. Retains practice catalog injection ({@code inputs/practices/})
 * and delivery-phase post-processing here — catalog injection is per-job and not provider-shaped.
 *
 * <p>Container workspace layout — read-only vs writable by LOCATION per ADR 0020 (see
 * {@code docs/developer/agent/workspace-abi.mdx} for the full ABI):
 * <pre>
 * /workspace/
 * ├── inputs/                            # read-only — the path-guard whitelists exactly this subtree
 * │   ├── manifest.json                  #   telescope: integration-agnostic index (path/connector/sha256)
 * │   ├── sources/scm/repo/            #   the SCM connector's source — git checkout (RO mount)
 * │   ├── context/                       #   workspace context (this handler populates via WorkspaceContextBuilder)
 * │   │   ├── metadata.json              #     PR metadata + commits
 * │   │   ├── comments.json              #     review comments
 * │   │   ├── diff.patch                 #     diff with [L&lt;n&gt;] annotations
 * │   │   ├── diff_summary.md            #     per-file diff chunks
 * │   │   └── contributor_history.json   #     prior findings (optional)
 * │   └── practices/{index.json, {slug}.md, all-criteria.md}
 * ├── work/                              # scratch the agent + precompute write; NEVER collected
 * │   ├── precompute/practices/{slug}.ts
 * │   ├── precompute-out/
 * │   └── analysis/
 * ├── out/                               # the ONLY directory collected back into SQL
 * ├── task.json                          # Task envelope (TaskEnvelope around Task.PracticeReview)
 * ├── .pi/{AGENTS.md, settings.json, extensions/} # Pi SDK agent dir ($PI_CODING_AGENT_DIR)
 * └── .run-pi.mjs                          # runner entry point
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
        ContentProvider.OUTPUT_PREFIX + "comments.json",
        // Raw SQL-only integration objects (the agent cannot get these from the mounted worktree): a finding
        // grounded in one of these must survive the diff-scope filter. Only objects absent from the worktree
        // belong here — anything derivable from the checkout is content the agent reads directly.
        ContentProvider.OUTPUT_PREFIX + "linked_work_items.json",
        ContentProvider.OUTPUT_PREFIX + "review_threads.json",
        // General (conversation-tab) MR review discussion — position-less notes GitLab routes to
        // IssueComment, surfaced by GeneralReviewCommentContentProvider. The reviewer-craft practices
        // ground in this alongside comments.json; a finding citing it must survive the diff-scope filter.
        ContentProvider.OUTPUT_PREFIX + "general_comments.json"
    );

    /**
     * Process/metadata-level PR practices whose evidence is the PR metadata, the commit subjects, or the
     * review thread — NOT a diff line. {@code filterByDiffScope} is a guard for CODE-defect findings whose
     * location must sit inside the diff; applied to these process practices it would wrongly drop a valid
     * finding the moment the agent attaches a stray (non-diff) location to it (e.g. a commit-subject
     * finding citing a commit ref). These slugs therefore bypass the diff-scope filter.
     */
    private static final Set<String> METADATA_LEVEL_PRACTICES = Set.of(
        "scope-one-reviewable-change",
        "describe-what-and-why",
        "ready-and-traceable-handoff",
        "commit-subjects-explain-each-change",
        "engaging-with-inline-review-comments",
        // Reviewer-side review practices ground in the review-decision/thread-state context file
        // (review_threads.json) or comments.json — never a diff line of the change under review.
        "reviews-substantively-with-understanding",
        "leaves-useful-specific-review-comments",
        "reviews-respectfully-asks-rather-than-demands",
        "mr-description-quality",
        "commit-discipline",
        // Cross-context practices: grounded in a neighbourhood context file, not a diff line.
        "honours-linked-issue-acceptance-criteria",
        "branches-from-the-integration-branch"
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
    private final ReactionSuppressionFilter reactionSuppressionFilter;

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
        SecretDiffScanner secretDiffScanner,
        ReactionSuppressionFilter reactionSuppressionFilter
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
        this.reactionSuppressionFilter = reactionSuppressionFilter;
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
        // The lifecycle event that triggered this job. When present, the catalog injector materialises
        // ONLY the practices whose triggerEvents include it — so an authoring practice is not re-litigated
        // on a fixup push and a retrospective practice runs only at merge. Null = run the full focus set
        // (the gate-bypass dev path / bot command).
        if (submissionRequest.triggerEvent() != null) {
            metadata.put("trigger_event", submissionRequest.triggerEvent());
        }

        // The trigger-event PHASE is part of the key: an authoring review (Created/Ready), a push
        // re-scan (Synchronized), a reviewer pass (ReviewSubmitted) and a retrospective (Merged) of the
        // SAME head SHA are DIFFERENT reviews over different practice sets — a retrospective must never be
        // deduped/cooled-down against an earlier authoring job for the same commit. Phase sits BEFORE the
        // SHA so extractCooldownKeyPrefix scopes cooldown per (pr, phase).
        String phase = submissionRequest.triggerEvent() != null ? submissionRequest.triggerEvent() : "manual";
        String idempotencyKey =
            "pr_review:" +
            pullRequestData.repository().nameWithOwner() +
            ":" +
            pullRequestData.number() +
            ":" +
            phase +
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

        // Pre-create blobs/scm/ so the repo can mount under it (the directory mount needs its parent
        // to exist before docker cp extracts into it).
        files.put(WorkspaceAbi.SCM_SOURCE_KEEP, new byte[0]);

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
        // out at the merge base (so a working-tree grep finds nothing). The synthetic NOT_OBSERVED
        // findings flow through the normal persist+compose+deliver path and force the green→red flip.
        List<PracticeDetectionResultParser.ValidatedFinding> secretFindings = scanForSecrets(
            job,
            parsed.validFindings()
        );

        boolean allNotApplicable = parsed
            .validFindings()
            .stream()
            .allMatch(f -> f.presence() == Presence.NOT_APPLICABLE);
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
                "Secret pre-pass injected {} hardcoded-secrets NOT_OBSERVED finding(s); blocking any all-clear comment: jobId={}",
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

        // Coherence coercion: keep (observation, severity) coherent regardless of what the
        // weak model emitted. A defect-detector practice's OBSERVED becomes NOT_APPLICABLE (no false strength
        // ships to the student), and severity is pinned to the INFO sentinel except on a NOT_OBSERVED gap.
        // Applied BEFORE deliver() so it reaches the DB, and before compose() so it reaches the posted comment.
        Set<String> defectDetectorSlugs =
            job.getWorkspace() == null
                ? Set.of()
                : practiceCatalogInjector.defectDetectorSlugs(job.getWorkspace().getId(), WorkArtifact.PULL_REQUEST);
        scopedFindings = new ArrayList<>(
            PracticeDetectionResultParser.coerceCoherence(scopedFindings, defectDetectorSlugs)
        );

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

        // Stamp each finding with the EXACT correlation key deliver() persisted (ADR 0021 C2), keyed by
        // identity so a delivered inline note can be matched back to its persisted finding without recomputing
        // the key downstream (which could drift). Done BEFORE the reaction filter so an escalated copy inherits
        // the key too. A finding absent from the map (unknown slug — never persisted, never delivered) is left
        // unstamped; it cannot reach compose() anyway since the filter only re-emits what was passed in.
        Map<PracticeDetectionResultParser.ValidatedFinding, String> findingFingerprints = result.findingFingerprints();
        for (int i = 0; i < scopedFindings.size(); i++) {
            String key = findingFingerprints.get(scopedFindings.get(i));
            if (key != null) {
                scopedFindings.set(i, scopedFindings.get(i).withRecurrenceKey(key));
            }
        }

        // Reaction-aware re-nag suppression (ADR 0021, B2): drop a locus the student already DISPUTED /
        // marked NOT_APPLICABLE on an earlier run, and stiffen the wording on an ADDRESSED-but-recurring
        // locus. Flag-gated; a no-op pass-through when off or when no reaction matches. Runs AFTER
        // deliver() because finding_fingerprint is persisted there; before compose() so the drop reaches both the
        // summary and the inline notes.
        ReactionSuppressionFilter.ReactionDecision reactions = reactionSuppressionFilter.evaluate(job, scopedFindings);
        List<PracticeDetectionResultParser.ValidatedFinding> deliverable = reactions.deliverable();
        if (deliverable.isEmpty() && !scopedFindings.isEmpty()) {
            // Everything this run was already reacted away — a SUCCESS (the student told us to stop nagging),
            // not a delivery failure. The SUPPRESSED ledger rows are written; the prior edit-in-place summary
            // stays as-is. Nothing new to post.
            log.info("All {} findings suppressed by prior reactions: jobId={}", scopedFindings.size(), job.getId());
            return;
        }

        Map<String, String> whyBySlug =
            job.getWorkspace() == null
                ? Map.of()
                : practiceCatalogInjector.whyBySlug(job.getWorkspace().getId(), WorkArtifact.PULL_REQUEST);
        PracticeDetectionResultParser.DeliveryContent delivery = DeliveryComposer.compose(
            deliverable,
            WorkArtifact.PULL_REQUEST,
            whyBySlug
        );
        if (delivery != null) {
            log.info("Server-side delivery composed from {} findings: jobId={}", deliverable.size(), job.getId());
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

        // Recompose hook: after the inline notes post, the summary's inline section is demoted to a pointer
        // for every finding whose comment actually landed (its detail then lives on the diff). Binding the
        // findings + kind here keeps FeedbackDeliveryService free of the composition inputs — it only
        // hands back the delivered keys. Re-runs the identical partition so the body cannot drift.
        feedbackService.deliverFeedback(job, delivery, deliveredKeys ->
            DeliveryComposer.recomposeMrNote(deliverable, WorkArtifact.PULL_REQUEST, whyBySlug, deliveredKeys)
        );
    }

    // Delivery-phase diff helpers (delegate to GitDiffOperations)

    /**
     * Run the deterministic secret pre-pass over the job's raw diff and map each hit to a synthetic
     * {@code hardcoded-secrets} NOT_OBSERVED finding. Hits already covered by an LLM-produced
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
                !"hardcoded-secrets".equals(f.practiceSlug()) ||
                f.assessment() != Assessment.BAD ||
                f.evidence() == null
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
        Severity severity = (hit.isCritical() && !lowSignal) ? Severity.CRITICAL : Severity.MAJOR;

        String reasoning =
            "A credential appears on a changed line: `" +
            hit.addedLine() +
            "`. Committed secrets remain in the git history permanently — even after the line is removed — so the key must be treated as compromised.";
        String guidance =
            "Remove the literal value, rotate the credential immediately, and load it at runtime from an environment variable or a secrets manager instead of hardcoding it.";

        return new PracticeDetectionResultParser.ValidatedFinding(
            "hardcoded-secrets",
            "Hardcoded secret on a changed line",
            Presence.PRESENT,
            Assessment.BAD,
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
                // The agent cites files it read under the repo mount as "inputs/sources/scm/repo/<path>" (ADR 0020),
                // but diff-stat paths are repo-relative ("<path>"). Strip the mount prefix so a code finding
                // on a genuinely-changed file is not dropped on a cosmetic path mismatch.
                String repoRelative = path.startsWith(WorkspaceAbi.REPO_MOUNT_RELATIVE)
                    ? path.substring(WorkspaceAbi.REPO_MOUNT_RELATIVE.length())
                    : path;
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
}

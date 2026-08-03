package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireInt;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireLong;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
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
 * orchestrates {@code PullRequestContentSource} → {@code inputs/context/...} files) and the
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

    private static final Set<String> ALLOWED_INTERNAL_CONTEXT_PATHS = Set.of(
        ContentSource.OUTPUT_PREFIX + "metadata.json",
        ContentSource.OUTPUT_PREFIX + "diff.patch",
        ContentSource.OUTPUT_PREFIX + "diff_summary.md",
        ContentSource.OUTPUT_PREFIX + "comments.json",
        ContentSource.OUTPUT_PREFIX + "linked_work_items.json",
        ContentSource.OUTPUT_PREFIX + "review_threads.json",
        ContentSource.OUTPUT_PREFIX + "general_comments.json"
    );

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
        "honours-linked-issue-acceptance-criteria"
    );

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewHandler.class);

    private final JsonMapper objectMapper;
    private final ContentAddressedStore cas;
    private final PracticeCatalogInjector practiceCatalogInjector;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final PracticeDetectionResultParser resultParser;
    private final PracticeDetectionDeliveryService deliveryService;
    private final FeedbackDeliveryService feedbackService;
    private final SecretDiffScanner secretDiffScanner;
    private final ReactionSuppressionFilter reactionSuppressionFilter;

    PullRequestReviewHandler(
        JsonMapper objectMapper,
        ContentAddressedStore cas,
        PracticeCatalogInjector practiceCatalogInjector,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService,
        SecretDiffScanner secretDiffScanner,
        ReactionSuppressionFilter reactionSuppressionFilter
    ) {
        this.objectMapper = objectMapper;
        this.cas = cas;
        this.practiceCatalogInjector = practiceCatalogInjector;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
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
        // (describe-what-and-why, commit-subjects-explain-each-change) — their precompute scripts read
        // metadata.title / metadata.body. Without these the practices silently can't evaluate.
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

    @Override
    public Map<String, byte[]> prepareInputFiles(AgentJob job) {
        long startNanos = System.nanoTime();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        long repositoryId = requireLong(metadata, "repository_id");
        long pullRequestId = requireLong(metadata, "pull_request_id");

        List<Practice> practices = practiceCatalogInjector.resolve(job, WorkArtifact.PULL_REQUEST);
        PreparedEvidence prepared = workspaceContextBuilder.prepare(
            new ContextRequest.PracticeReviewRequest(job),
            EvidencePlan.compile(practices)
        );
        practices = workspaceContextBuilder.readyPractices(
            prepared.manifest(),
            practices,
            job.getId().toString(),
            job.getCreatedAt()
        );
        if (practices.isEmpty()) {
            throw new JobPreparationException("No practice has sufficient evidence: jobId=" + job.getId());
        }
        prepared = workspaceContextBuilder.restrictTo(prepared, EvidencePlan.compile(practices));
        Map<String, byte[]> files = new LinkedHashMap<>(prepared.files());

        files.put(SandboxLayout.TASK_ENVELOPE_FILENAME, taskEnvelopeWriter.write(buildTaskEnvelope(job, metadata)));

        practiceCatalogInjector.inject(files, job, WorkArtifact.PULL_REQUEST, practices);

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
            SandboxLayout.ORCHESTRATOR_PATH +
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

        String unifiedDiff = capturedDiff(job);
        Set<String> diffFiles = Set.copyOf(DiffHunkValidator.parseValidLines(unifiedDiff).keySet());
        Set<String> defectDetectorSlugs = practiceCatalogInjector.defectDetectorSlugs(job);
        List<PracticeDetectionResultParser.ValidatedFinding> secretFindings = practiceCatalogInjector.isAdmitted(
            job,
            "avoids-insecure-defaults-and-over-broad-permissions"
        )
            ? scanForSecrets(unifiedDiff)
            : List.of();

        boolean allNotApplicable = parsed
            .validFindings()
            .stream()
            .allMatch(f -> f.presence() == Presence.NOT_APPLICABLE);
        if (allNotApplicable && secretFindings.isEmpty()) {
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
            Set<String> scannerLocations = secretFindings
                .stream()
                .flatMap(f -> f.evidence().path("locations").valueStream())
                .map(location -> location.path("path").asString() + ":" + location.path("startLine").asInt())
                .collect(java.util.stream.Collectors.toSet());
            scopedFindings.removeIf(
                finding ->
                    "avoids-insecure-defaults-and-over-broad-permissions".equals(finding.practiceSlug()) &&
                    finding.evidence() != null &&
                    finding
                        .evidence()
                        .path("locations")
                        .valueStream()
                        .anyMatch(location ->
                            scannerLocations.contains(
                                location.path("path").asString() + ":" + location.path("startLine").asInt()
                            )
                        )
            );
            scopedFindings.addAll(secretFindings);
            log.warn(
                "Secret pre-pass injected {} avoids-insecure-defaults-and-over-broad-permissions PRESENT/BAD finding(s); blocking any all-clear comment: jobId={}",
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
        // weak model emitted. A defect-detector practice's GOOD assessment becomes NOT_APPLICABLE (no false strength
        // ships to the student), and severity is pinned to the INFO sentinel except on a BAD finding.
        // Applied BEFORE deliver() so it reaches the DB, and before compose() so it reaches the posted comment.
        scopedFindings = new ArrayList<>(
            PracticeDetectionResultParser.coerceCoherence(scopedFindings, defectDetectorSlugs)
        );

        PracticeDetectionDeliveryService.DeliveryResult result;
        try {
            result = deliveryService.deliver(job, scopedFindings);
            log.info(
                "Delivery complete: inserted={}, duplicate={}, jobId={}",
                result.inserted(),
                result.discardedDuplicate(),
                job.getId()
            );
        } catch (JobDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new JobDeliveryException("Delivery failed unexpectedly: jobId=" + job.getId(), e);
        }

        // Stamp each finding with the EXACT keys deliver() persisted (ADR 0021 C2), by identity, so downstream
        // stages address the stored observation without recomputing a key that could drift. Done BEFORE the
        // reaction filter so an escalated copy inherits them. A finding absent from the map (unknown slug —
        // never persisted) stays unstamped.
        Map<PracticeDetectionResultParser.ValidatedFinding, ObservationKeys> keysByFinding = result.observationKeys();
        for (int i = 0; i < scopedFindings.size(); i++) {
            scopedFindings.set(i, scopedFindings.get(i).withKeys(keysByFinding.get(scopedFindings.get(i))));
        }

        // Reaction-aware re-nag suppression (ADR 0021, B2): drop a locus the student already DISPUTED /
        // marked NOT_APPLICABLE on an earlier run, and stiffen the wording on an ADDRESSED-but-recurring
        // locus. Flag-gated; a no-op pass-through when off or when no reaction matches. Runs AFTER
        // deliver() because recurrence_key is persisted there; before compose() so the drop reaches both the
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

        // Silent-clean-on-stale-diff signal: the NOT_APPLICABLE guard above only fires when EVERY finding
        // is NA. A weak model that instead reads a stale/empty diff as "all clean" — emitting only
        // ABSENT/GOOD strengths (no BAD) — slips past that guard and composes an all-clear over an artifact
        // that was effectively never diffed. We do NOT throw (a genuinely clean PR over a non-empty diff is
        // legitimate strengths-only), but a strengths-only delivery while diffFiles is EMPTY is the stale-diff
        // fingerprint — surface it so the case is observable rather than silent.
        boolean hasGap = deliverable.stream().anyMatch(f -> f.assessment() == Assessment.BAD);
        if (!hasGap && diffFiles.isEmpty()) {
            log.warn(
                "Composing a strengths-only delivery over an EMPTY diff ({} finding(s), no BAD): the diff may " +
                    "be stale/unavailable, so this all-clear is not grounded in changed code. jobId={}",
                deliverable.size(),
                job.getId()
            );
        }

        Map<String, String> whyBySlug =
            job.getWorkspace() == null
                ? Map.of()
                : practiceCatalogInjector.whyBySlug(job.getWorkspace().getId(), WorkArtifact.PULL_REQUEST);
        // unifiedDiff (computed once at the top of deliver()) is the substrate for BOTH the M1 grounding
        // guard (drop a hallucinated inline anchor before it lands on a student) and the downstream
        // line-position validator below.
        PracticeDetectionResultParser.DeliveryContent delivery = DeliveryComposer.compose(
            deliverable,
            WorkArtifact.PULL_REQUEST,
            whyBySlug,
            unifiedDiff
        );
        if (delivery != null) {
            log.info("Server-side delivery composed from {} findings: jobId={}", deliverable.size(), job.getId());
            if (!delivery.diffNotes().isEmpty()) {
                var validLines =
                    unifiedDiff == null
                        ? Map.<String, TreeSet<Integer>>of()
                        : DiffHunkValidator.parseValidLines(unifiedDiff);
                if (!validLines.isEmpty()) {
                    var correctedNotes = DiffHunkValidator.validateAndCorrect(
                        delivery.diffNotes(),
                        validLines,
                        job.getId().toString()
                    );
                    delivery = delivery.withDiffNotes(correctedNotes);
                }
            }
        }

        // Recompose hook: after the inline notes post, the summary's inline section is demoted to a pointer
        // for every finding whose comment actually landed (its detail then lives on the diff). Binding the
        // findings + work artifact here keeps FeedbackDeliveryService free of the composition inputs — it only
        // hands back the delivered keys. Re-runs the identical partition so the body cannot drift.
        feedbackService.deliverFeedback(job, delivery, deliveredKeys ->
            DeliveryComposer.recomposeMrNote(deliverable, WorkArtifact.PULL_REQUEST, whyBySlug, deliveredKeys)
        );
    }

    /**
     * Guards only the summary comment. Inline diff notes are reconciled on every delivery attempt, so a
     * recovery retry that falls through to {@link #deliver} cannot duplicate them.
     */
    @Override
    public ExistingDeliveryLookup findExistingDelivery(AgentJob job) {
        return feedbackService.findExistingDeliveryCommentId(job);
    }

    private List<PracticeDetectionResultParser.ValidatedFinding> scanForSecrets(@Nullable String diff) {
        List<SecretDiffScanner.SecretHit> hits = secretDiffScanner.scan(diff);
        if (hits.isEmpty()) return List.of();

        List<PracticeDetectionResultParser.ValidatedFinding> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SecretDiffScanner.SecretHit hit : hits) {
            String key = hit.path() + ":" + hit.newLine() + ":" + hit.ruleId();
            if (!seen.add(key)) continue;
            out.add(toSecretFinding(hit));
        }
        return out;
    }

    private PracticeDetectionResultParser.ValidatedFinding toSecretFinding(SecretDiffScanner.SecretHit hit) {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("detector", "secret-diff-scanner");
        evidence.putArray("sourceKinds").add("scm.pull-request.diff");
        ArrayNode citations = evidence.putArray("citations");
        ObjectNode citation = citations.addObject();
        citation.put("sourceKind", "scm.pull-request.diff");
        citation.put("artifactPath", ContentSource.OUTPUT_PREFIX + "diff.patch");
        citation.put("path", hit.path());
        citation.put("side", "NEW");
        citation.put("startLine", hit.newLine());
        citation.put("endLine", hit.newLine());
        citation.put("quote", hit.addedLine());
        ArrayNode locations = evidence.putArray("locations");
        ObjectNode location = locations.addObject();
        location.put("path", hit.path());
        location.put("startLine", hit.newLine());
        ArrayNode snippets = evidence.putArray("snippets");
        snippets.add(hit.addedLine());

        boolean lowSignal = secretDiffScanner.isLowSignalPath(hit.path());
        Severity severity = lowSignal ? Severity.MINOR : Severity.MAJOR;

        String reasoning =
            "A credential appears on a changed line: `" +
            hit.addedLine() +
            "`. Committed secrets remain in the git history permanently — even after the line is removed — so the key must be treated as compromised.";
        String guidance =
            "Remove the literal value, rotate the credential immediately, and load it at runtime from an environment variable or a secrets manager instead of hardcoding it.";

        return new PracticeDetectionResultParser.ValidatedFinding(
            "avoids-insecure-defaults-and-over-broad-permissions",
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

    private @Nullable String capturedDiff(AgentJob job) {
        JsonNode sources =
            job.getEvidenceSnapshot() == null ? null : job.getEvidenceSnapshot().path("manifest").path("sources");
        if (sources == null || !sources.isArray()) {
            throw new JobDeliveryException("Job has no captured source manifest: jobId=" + job.getId());
        }
        for (JsonNode source : sources) {
            if (
                !"scm.pull-request.diff".equals(source.path("kind").asString()) ||
                !"AVAILABLE".equals(source.path("availability").asString())
            ) {
                continue;
            }
            for (JsonNode artifact : source.path("artifacts")) {
                if ((ContentSource.OUTPUT_PREFIX + "diff.patch").equals(artifact.path("path").asString())) {
                    String sha = artifact.path("sha256").asString();
                    byte[] bytes = cas
                        .get(sha)
                        .orElseThrow(() ->
                            new JobDeliveryException("Captured diff is no longer available: jobId=" + job.getId())
                        );
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
            throw new JobDeliveryException("Captured diff source has no diff artifact: jobId=" + job.getId());
        }
        return null;
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
                String repoRelative = path.startsWith(SandboxLayout.REPO_MOUNT_RELATIVE)
                    ? path.substring(SandboxLayout.REPO_MOUNT_RELATIVE.length())
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

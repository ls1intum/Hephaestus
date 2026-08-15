package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireInt;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireLong;
import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
 * │   ├── sources/scm/repo/              #   optional materialized repository tree
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
    private final InContextDeliveryGate inContextDeliveryGate;

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
        ReactionSuppressionFilter reactionSuppressionFilter,
        InContextDeliveryGate inContextDeliveryGate
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
        this.inContextDeliveryGate = inContextDeliveryGate;
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
        metadata.put(
            PracticeDetectionDeliveryService.ORIGIN_METADATA_KEY,
            submissionRequest.observationOrigin().name()
        );
        metadata.put("repository_id", pullRequestData.repository().id());
        metadata.put("repository_full_name", pullRequestData.repository().nameWithOwner());
        metadata.put("pull_request_id", pullRequestData.id());
        metadata.put("pr_number", pullRequestData.number());
        metadata.put("pr_url", pullRequestData.htmlUrl());
        metadata.put("commit_sha", submissionRequest.headRefOid());
        metadata.put("source_branch", submissionRequest.headRefName());
        metadata.put("target_branch", submissionRequest.baseRefName());
        // The sole inputs for the communication/process practices (describe-what-and-why,
        // commit-subjects-explain-each-change) — their precompute scripts read metadata.title / .body.
        metadata.put("title", pullRequestData.title());
        metadata.put("body", pullRequestData.body());
        // When present, the catalog injector materialises ONLY the practices bound to this signal, so an
        // authoring practice is not re-litigated on a fixup push. Null = run the full focus set.
        if (submissionRequest.triggerSignal() != null) {
            metadata.put(PracticeCatalogInjector.SIGNAL_METADATA_KEY, submissionRequest.triggerSignal().value());
        }

        // The occasion is part of the key: an authoring review, a push re-scan, a reviewer pass and a
        // retrospective of the SAME head SHA are DIFFERENT reviews over different practice sets, so a
        // retrospective must never be deduped against an earlier authoring job for the same commit. It
        // sits BEFORE the SHA so extractCooldownKeyPrefix scopes cooldown per (pr, occasion).
        String phase = submissionRequest.triggerSignal() != null ? submissionRequest.triggerSignal().value() : "manual";
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
    public PreparedJobInputs prepareInputs(AgentJob job) {
        long startNanos = System.nanoTime();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        long repositoryId = requireLong(metadata, "repository_id");
        long pullRequestId = requireLong(metadata, "pull_request_id");

        SignalName signal = PracticeCatalogInjector.signalOf(job);
        List<Practice> practices = practiceCatalogInjector.resolveEligiblePractices(job, ArtifactKinds.PULL_REQUEST);
        PreparedEvidence prepared = workspaceContextBuilder.prepare(
            new ContextRequest.PracticeReviewRequest(job),
            EvidencePlan.compile(practices)
        );
        var artifactSourceManifest = prepared.manifest();
        var readiness = workspaceContextBuilder.prepareAutomatedReviewReadiness(
            prepared.manifest(),
            practices,
            job.getId().toString(),
            job.getCreatedAt(),
            signal
        );
        List<Practice> eligible = practices;
        practices = readiness.readyPractices();
        // A practice skipped for insufficient evidence leaves no trace in the delivered review, so a
        // reader cannot distinguish it from one that was assessed and produced no findings; the readiness
        // report records the same list for the administration surface.
        if (practices.size() < eligible.size()) {
            log.info(
                "Skipping {} of {} practice(s) for insufficient evidence: jobId={}, skipped={}",
                eligible.size() - practices.size(),
                eligible.size(),
                job.getId(),
                readiness
                    .report()
                    .decisions()
                    .stream()
                    .filter(decision -> !decision.ready())
                    .map(decision -> decision.practiceSlug() + decision.reasonCodes())
                    .toList()
            );
        }
        if (practices.isEmpty()) {
            throw new InsufficientEvidenceException(
                "No practice has sufficient evidence: jobId=" + job.getId(),
                new PreparedJobInputs(
                    prepared.files(),
                    prepared.filesOnDisk(),
                    prepared.cleanups(),
                    artifactSourceManifest,
                    readiness.report()
                )
            );
        }
        Map<String, byte[]> files = new LinkedHashMap<>(prepared.files());

        files.put(SandboxLayout.TASK_ENVELOPE_FILENAME, taskEnvelopeWriter.write(buildTaskEnvelope(job, metadata)));

        practiceCatalogInjector.inject(files, job, ArtifactKinds.PULL_REQUEST, practices);
        ContextMapWriter.write(files);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(
            "Context preparation complete: {} files, {} ms, repoId={}, pullRequestId={}",
            files.size(),
            elapsedMs,
            repositoryId,
            pullRequestId
        );
        return new PreparedJobInputs(
            files,
            prepared.filesOnDisk(),
            prepared.cleanups(),
            artifactSourceManifest,
            readiness.report()
        );
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

        // A run that decided nothing at all over a non-empty diff is the stale-diff signature. Both
        // valence-free presences count here: an empty diff yields NOT_APPLICABLE, a truncated one yields
        // INCONCLUSIVE, and the harness fault is identical either way.
        boolean nothingDecided = parsed
            .validFindings()
            .stream()
            .noneMatch(f -> f.presence().carriesValence());
        if (nothingDecided && secretFindings.isEmpty()) {
            boolean hasDiffContent = !diffFiles.isEmpty();
            if (hasDiffContent) {
                throw new JobDeliveryException(
                    "No finding decided anything (all NOT_APPLICABLE/INCONCLUSIVE) but the diff contains " +
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
                .flatMap(f -> f.evidence().path("citations").valueStream())
                .map(citation -> citation.path("path").asString() + ":" + citation.path("startLine").asInt())
                .collect(java.util.stream.Collectors.toSet());
            scopedFindings.removeIf(
                finding ->
                    "avoids-insecure-defaults-and-over-broad-permissions".equals(finding.practiceSlug()) &&
                    finding.evidence() != null &&
                    finding
                        .evidence()
                        .path("citations")
                        .valueStream()
                        .anyMatch(citation ->
                            scannerLocations.contains(
                                citation.path("path").asString() + ":" + citation.path("startLine").asInt()
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

        // Coherence coercion: a defect-detector practice's GOOD assessment becomes NOT_APPLICABLE (no false
        // strength ships to the student), and severity is pinned to the INFO sentinel except on a BAD
        // finding. Applied BEFORE deliver() so it reaches the DB, and before compose() so it reaches the
        // posted comment.
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

        // Stamp each finding with the exact keys deliver() persisted, by identity, so downstream stages
        // address the stored observation without recomputing a key that could drift.
        Map<PracticeDetectionResultParser.ValidatedFinding, ObservationKeys> keysByFinding = result.observationKeys();
        for (int i = 0; i < scopedFindings.size(); i++) {
            scopedFindings.set(i, scopedFindings.get(i).withKeys(keysByFinding.get(scopedFindings.get(i))));
        }

        // Loudness tier BEFORE the reaction filter (ADR 0021): the workspace's standing policy on how loud
        // a practice may be settles first, so a finding it already chose not to place on the artifact is
        // never also charged to the developer's own per-locus reaction history. Runs AFTER deliver()
        // because recurrence_key is persisted there; before compose() so the drop reaches both the summary
        // and the inline notes.
        List<PracticeDetectionResultParser.ValidatedFinding> loudEnough = inContextDeliveryGate.admitInContext(
            job,
            scopedFindings
        );
        if (loudEnough.isEmpty() && !scopedFindings.isEmpty()) {
            // The observations are persisted and the SUPPRESSED rows are written; posting an empty summary
            // would be the noise the tier was turned down to avoid.
            log.info("All {} findings withheld by loudness tier: jobId={}", scopedFindings.size(), job.getId());
            return;
        }

        ReactionSuppressionFilter.ReactionDecision reactions = reactionSuppressionFilter.evaluate(job, loudEnough);
        List<PracticeDetectionResultParser.ValidatedFinding> deliverable = reactions.deliverable();
        if (deliverable.isEmpty() && !scopedFindings.isEmpty()) {
            // A SUCCESS (the student told us to stop nagging), not a delivery failure: the SUPPRESSED
            // ledger rows are written, and the prior edit-in-place summary stays as-is.
            log.info("All {} findings suppressed by prior reactions: jobId={}", scopedFindings.size(), job.getId());
            return;
        }

        // The NOT_APPLICABLE guard above only fires when EVERY finding is NA. A weak model that instead
        // reads a stale/empty diff as "all clean" (only ABSENT/GOOD, no BAD) slips past it and composes an
        // all-clear over an artifact that was effectively never diffed. Not thrown — a genuinely clean PR
        // is legitimate strengths-only — but surfaced so the case is observable rather than silent.
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
                : practiceCatalogInjector.whyBySlug(job.getWorkspace(), ArtifactKinds.PULL_REQUEST);
        // unifiedDiff is the substrate for both the grounding guard (drop a hallucinated inline anchor
        // before it lands on a student) and the line-position validator below.
        PracticeDetectionResultParser.DeliveryContent delivery = DeliveryComposer.compose(
            deliverable,
            ArtifactKinds.PULL_REQUEST,
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
        // for every finding whose comment actually landed. Keeps FeedbackDeliveryService free of the
        // composition inputs — it only hands back the delivered keys.
        feedbackService.deliverFeedback(job, delivery, deliveredKeys ->
            DeliveryComposer.recomposeMrNote(deliverable, ArtifactKinds.PULL_REQUEST, whyBySlug, deliveredKeys)
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
        ArrayNode citations = evidence.putArray("citations");
        ObjectNode citation = citations.addObject();
        citation.put("sourceKind", "scm.pull-request.diff");
        citation.put("artifactPath", ContentSource.OUTPUT_PREFIX + "diff.patch");
        citation.put("path", hit.path());
        citation.put("side", "NEW");
        citation.put("startLine", hit.newLine());
        citation.put("endLine", hit.newLine());
        citation.put("quoteSha256", ProvenanceDigest.sha256Hex(hit.addedLine().getBytes(StandardCharsets.UTF_8)));
        citation.put("quoteRedacted", true);

        boolean lowSignal = secretDiffScanner.isLowSignalPath(hit.path());
        Severity severity = lowSignal ? Severity.MINOR : Severity.MAJOR;

        String reasoning =
            "A credential appears on the cited changed line. Committed secrets remain in git history even after removal, so treat the credential as compromised.";
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
                !"AVAILABLE".equals(source.path("state").path("availability").asString())
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

    static List<PracticeDetectionResultParser.ValidatedFinding> filterByDiffScope(
        List<PracticeDetectionResultParser.ValidatedFinding> findings,
        Set<String> diffFiles
    ) {
        if (diffFiles.isEmpty()) return findings;
        List<PracticeDetectionResultParser.ValidatedFinding> filtered = new ArrayList<>();
        for (var finding : findings) {
            JsonNode evidence = finding.evidence();
            if (evidence == null || evidence.isNull() || evidence.isMissingNode()) {
                filtered.add(finding);
                continue;
            }
            JsonNode citations = evidence.get("citations");
            if (citations == null || !citations.isArray() || citations.isEmpty()) {
                filtered.add(finding);
                continue;
            }
            boolean hasInScopeLocation = false;
            for (JsonNode citation : citations) {
                String sourceKind = citation.path("sourceKind").asString();
                if (sourceKind.isBlank()) {
                    continue;
                }
                if (!"scm.pull-request.diff".equals(sourceKind)) {
                    hasInScopeLocation = true;
                    break;
                }
                JsonNode pathNode = citation.get("path");
                if (pathNode == null || pathNode.isNull() || pathNode.isMissingNode()) {
                    continue;
                }
                String path = pathNode.asString();
                if (path.isBlank() || "null".equals(path)) {
                    continue;
                }
                String repoRelative = path.startsWith(SandboxLayout.REPO_MOUNT_RELATIVE)
                    ? path.substring(SandboxLayout.REPO_MOUNT_RELATIVE.length())
                    : path;
                if (diffFiles.contains(path) || diffFiles.contains(repoRelative)) {
                    hasInScopeLocation = true;
                    break;
                }
            }
            if (hasInScopeLocation) {
                filtered.add(finding);
            } else {
                log.info("Filtered out-of-scope finding: slug={}, citations={}", finding.practiceSlug(), citations);
            }
        }
        return filtered;
    }
}

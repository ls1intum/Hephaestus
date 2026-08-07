package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceLimitation;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Injects the practice registry, criteria, and precompute scripts into a job's workspace under
 * {@code inputs/practices/} and {@code work/precompute/}. Shared by every {@link de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler}
 * regardless of artifact — the catalog is per-job (workspace-active practices), not provider-shaped,
 * so it does not live behind the {@code ContentSource} SPI.
 *
 * <p>Filters by {@link ArtifactKind}: a PR job injects only PR-focus practices, an issue job only
 * issue-focus practices — so a diff-anchored practice never reaches an issue (and vice-versa).
 */
class PracticeCatalogInjector {

    /** Job-metadata key naming the signal that occasioned the review. */
    static final String SIGNAL_METADATA_KEY = "signal";

    private static final Logger log = LoggerFactory.getLogger(PracticeCatalogInjector.class);

    private final JsonMapper objectMapper;
    private final PracticeRepository practiceRepository;

    PracticeCatalogInjector(JsonMapper objectMapper, PracticeRepository practiceRepository) {
        this.objectMapper = objectMapper;
        this.practiceRepository = practiceRepository;
    }

    /**
     * Resolve {@code slug -> whyItMatters} for the {@code focus}-scoped active practices of a workspace,
     * surfaced verbatim as the "Why this matters" line on critiques. Deliberately NOT written into the model
     * workspace — only {@code getCriteria()} reaches the agent — so the principle stays server-controlled and
     * cannot be fabricated or drift in model prose. Practices with a blank principle are omitted.
     */
    Map<String, String> whyBySlug(Long workspaceId, ArtifactKind focus) {
        return practiceRepository
            .findByWorkspaceIdAndReviewTierNotAndArtifactKind(workspaceId, PracticeReviewTier.OFF, focus)
            .stream()
            .filter(p -> p.getWhyItMatters() != null && !p.getWhyItMatters().isBlank())
            .collect(Collectors.toMap(Practice::getSlug, Practice::getWhyItMatters, (a, b) -> a));
    }

    /**
     * The slugs of {@code focus}-scoped active practices that declare {@code DEFECT-DETECTOR DISCIPLINE} in
     * their criteria — i.e. practices with no legal {@code (PRESENT, GOOD)} clean-bill-of-health observation
     * (a clean surface is {@code NOT_APPLICABLE}, never a good reading). The delivery layer uses this to coerce
     * a model-emitted {@code (PRESENT, GOOD)} to {@code NOT_APPLICABLE} before it ships to the student as a
     * false strength (see {@code ValidatedFinding#coerceCoherence}).
     */
    Set<String> defectDetectorSlugs(AgentJob job) {
        Set<String> slugs = new HashSet<>();
        for (JsonNode practice : admittedPractices(job)) {
            if (practice.path("defectDetector").asBoolean(false)) {
                slugs.add(practice.path("slug").asString());
            }
        }
        return Set.copyOf(slugs);
    }

    private static JsonNode admittedPractices(AgentJob job) {
        JsonNode snapshot = job.getEvidenceSnapshot();
        JsonNode practices = snapshot == null ? null : snapshot.path("practices");
        if (practices == null || !practices.isArray()) {
            throw new JobDeliveryException("Job has no admitted practice snapshot: jobId=" + job.getId());
        }
        return practices;
    }

    boolean isAdmitted(AgentJob job, String slug) {
        for (JsonNode practice : admittedPractices(job)) {
            if (slug.equals(practice.path("slug").asString()) && practice.path("revisionId").isIntegralNumber()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inject the {@code focus}-scoped active practice catalog for {@code job}'s workspace into
     * {@code files}.
     *
     * @throws JobPreparationException if the job has no workspace, no matching active practices, or a
     *     slug violates the workspace ABI pattern.
     */
    void inject(Map<String, byte[]> files, AgentJob job, ArtifactKind focus) {
        inject(files, job, focus, resolveEligiblePractices(job, focus));
    }

    List<Practice> resolveEligiblePractices(AgentJob job, ArtifactKind focus) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();
        List<Practice> practices = practiceRepository
            .findByWorkspaceIdAndReviewTierNotAndArtifactKind(workspaceId, PracticeReviewTier.OFF, focus)
            .stream()
            .sorted(Comparator.comparing(Practice::getSlug))
            .toList();
        SignalName signal = signalOf(job);
        if (signal != null) {
            practices = practices
                .stream()
                .filter(p ->
                    p
                        .getBindings()
                        .stream()
                        .anyMatch(binding -> binding.matches(signal))
                )
                .toList();
        }
        if (practices.isEmpty()) {
            throw new JobPreparationException(
                "No active " + focus + " practices for workspace: workspaceId=" + workspaceId + ", jobId=" + job.getId()
            );
        }
        for (Practice p : practices) {
            String slug = p.getSlug();
            if (slug == null || !SandboxLayout.PRACTICE_SLUG.matcher(slug).matches()) {
                throw new JobPreparationException(
                    "Practice slug fails ABI pattern " + SandboxLayout.PRACTICE_SLUG.pattern() + ": " + slug
                );
            }
        }
        return practices;
    }

    void inject(Map<String, byte[]> files, AgentJob job, ArtifactKind focus, List<Practice> practices) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();

        ArrayNode index = objectMapper.createArrayNode();
        for (Practice p : practices) {
            String areaSlug = p.getArea() != null ? p.getArea().getSlug() : p.getSlug();
            ObjectNode entry = index.addObject();
            entry.put("slug", p.getSlug());
            entry.put("name", p.getName());
            entry.put("area", areaSlug);
            if (p.getCurrentRevision() == null || p.getCurrentRevision().getId() == null) {
                throw new JobPreparationException("Practice has no current revision: " + p.getSlug());
            }
            entry.put("revisionId", p.getCurrentRevision().getId());
            entry.put("defectDetector", p.isDefectDetector());
            ArrayNode allowedSources = entry.putArray("allowedSources");
            // Only what the bindings this occasion matched actually read. A practice bound to both a
            // merge and an opening may read a decision record on one and not the other; listing the
            // union would tell the model it may cite evidence this run never staged.
            PracticeBinding.needsFor(p.getBindings(), signalOf(job))
                .stream()
                .map(need -> need.sourceKind().value())
                .distinct()
                .sorted()
                .forEach(allowedSources::add);
        }
        try {
            files.put(SandboxLayout.PRACTICES_PREFIX + "index.json", objectMapper.writeValueAsBytes(index));
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize practice index.json: " + e.getMessage());
        }

        StringBuilder bundle = new StringBuilder();
        for (Practice p : practices) {
            String criteria = p.getCriteria() + renderKnownLimitations(p);
            files.put(SandboxLayout.PRACTICES_PREFIX + p.getSlug() + ".md", criteria.getBytes(StandardCharsets.UTF_8));
            bundle.append("# ").append(p.getSlug()).append("\n\n").append(criteria).append("\n\n---\n\n");
        }
        files.put(
            SandboxLayout.PRACTICES_PREFIX + "all-criteria.md",
            bundle.toString().getBytes(StandardCharsets.UTF_8)
        );

        files.put(SandboxLayout.ANALYSIS_PRACTICES_PREFIX + ".gitkeep", new byte[0]);

        int precomputeCount = 0;
        for (Practice p : practices) {
            String script = p.getPrecomputeScript();
            if (script != null && !script.isBlank()) {
                files.put(
                    SandboxLayout.PRECOMPUTE_PREFIX + "practices/" + p.getSlug() + ".ts",
                    script.getBytes(StandardCharsets.UTF_8)
                );
                precomputeCount++;
            }
        }

        log.info(
            "Injected practice catalog: {} {} practices ({} with precompute), workspaceId={}, jobId={}",
            practices.size(),
            focus,
            precomputeCount,
            workspaceId,
            job.getId()
        );
    }

    /** The claims this practice's evidence cannot support, appended to the criteria staged for the model. */
    private static String renderKnownLimitations(Practice p) {
        List<PracticeEvidenceLimitation> limitations = p.getAutomatedReviewPolicy().knownLimitations();
        if (limitations.isEmpty()) {
            return "";
        }
        // Each bullet states a limit of the evidence, not a claim to avoid. Reading them as claims
        // inverts the instruction: it would forbid the hedge and leave the overclaim it guards against.
        StringBuilder section = new StringBuilder("\n\n## What this evidence cannot show\n\n");
        section.append("Do not state or imply any conclusion that would require going beyond these limits:\n\n");
        for (PracticeEvidenceLimitation limitation : limitations) {
            section.append("- ").append(limitation.description()).append("\n");
        }
        return section.toString();
    }

    /**
     * The signal that occasioned this job, or {@code null} when nobody named one.
     *
     * <p>Null is the gate-bypass path — a review somebody asked for by hand — and it means every active
     * practice of the kind runs, reading everything any of its bindings reads. Narrowing that to one
     * binding would answer a narrower question than the one asked.
     */
    @Nullable
    static SignalName signalOf(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return null;
        }
        JsonNode node = metadata.get(SIGNAL_METADATA_KEY);
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString();
        return (value == null || value.isBlank()) ? null : SignalName.of(value);
    }
}

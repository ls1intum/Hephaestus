package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.charset.StandardCharsets;
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
 * <p>Filters by {@link WorkArtifact}: a PR job injects only PR-focus practices, an issue job only
 * issue-focus practices — so a diff-anchored practice never reaches an issue (and vice-versa).
 *
 * <p>Package-private; instantiated as a {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class PracticeCatalogInjector {

    private static final Logger log = LoggerFactory.getLogger(PracticeCatalogInjector.class);

    private final JsonMapper objectMapper;
    private final PracticeRepository practiceRepository;

    PracticeCatalogInjector(JsonMapper objectMapper, PracticeRepository practiceRepository) {
        this.objectMapper = objectMapper;
        this.practiceRepository = practiceRepository;
    }

    /**
     * Resolve {@code slug -> whyItMatters} (the catalogue-authored transferable principle) for the
     * {@code focus}-scoped active practices of a workspace. The delivery layer surfaces this verbatim as the
     * "Why this matters" feed-forward line on critiques (see {@code DeliveryComposer#appendPrinciple}). It is
     * deliberately NOT written into the model workspace — only {@code getCriteria()} reaches the agent — so the
     * principle stays server-controlled and cannot be fabricated or drift in model prose. Practices with a
     * blank principle are omitted, leaving their delivery unchanged.
     */
    Map<String, String> whyBySlug(Long workspaceId, WorkArtifact focus) {
        return practiceRepository
            .findByWorkspaceIdAndActiveTrueAndArtifactType(workspaceId, focus)
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
    Set<String> defectDetectorSlugs(Long workspaceId, WorkArtifact focus) {
        return practiceRepository
            .findByWorkspaceIdAndActiveTrueAndArtifactType(workspaceId, focus)
            .stream()
            .filter(Practice::isDefectDetector)
            .map(Practice::getSlug)
            .collect(Collectors.toSet());
    }

    /**
     * Inject the {@code focus}-scoped active practice catalog for {@code job}'s workspace into
     * {@code files}.
     *
     * @throws JobPreparationException if the job has no workspace, no matching active practices, or a
     *     slug violates the workspace ABI pattern.
     */
    void inject(Map<String, byte[]> files, AgentJob job, WorkArtifact focus) {
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        Long workspaceId = job.getWorkspace().getId();
        List<Practice> practices = practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(workspaceId, focus);
        // Lifecycle phase-correctness: when the job carries the trigger event that spawned it, materialise
        // ONLY the practices whose triggerEvents include that event — so an authoring practice is not
        // re-litigated on a fixup push (PullRequestSynchronized), a reviewer practice runs only after a
        // review exists, and a retrospective practice runs only at merge/close. A job with no trigger_event
        // (the gate-bypass dev path / bot command) keeps the full focus set.
        String triggerEvent = triggerEventOf(job);
        if (triggerEvent != null) {
            List<Practice> matched = practices
                .stream()
                .filter(p -> containsTriggerEvent(p.getTriggerEvents(), triggerEvent))
                .toList();
            if (!matched.isEmpty()) {
                practices = matched;
            }
            // If nothing matched (mis-seeded triggerEvents), fall through to the full set rather than fail
            // the job — the gate already confirmed at least one practice matched before submission.
        }
        if (practices.isEmpty()) {
            throw new JobPreparationException(
                "No active " + focus + " practices for workspace: workspaceId=" + workspaceId + ", jobId=" + job.getId()
            );
        }
        // Defense in depth: slugs are interpolated into filesystem paths below; reject any value that
        // doesn't match the ABI pattern before it can escape "inputs/practices/" / "work/precompute/".
        for (Practice p : practices) {
            String slug = p.getSlug();
            if (slug == null || !SandboxLayout.PRACTICE_SLUG.matcher(slug).matches()) {
                throw new JobPreparationException(
                    "Practice slug fails ABI pattern " + SandboxLayout.PRACTICE_SLUG.pattern() + ": " + slug
                );
            }
        }

        ArrayNode index = objectMapper.createArrayNode();
        for (Practice p : practices) {
            // area groups practices for the runner's per-area evaluation; falls back to the slug so an
            // ungrouped practice still forms its own one-practice group.
            String areaSlug = p.getArea() != null ? p.getArea().getSlug() : p.getSlug();
            ObjectNode entry = index.addObject();
            entry.put("slug", p.getSlug());
            entry.put("name", p.getName());
            entry.put("area", areaSlug);
        }
        try {
            files.put(SandboxLayout.PRACTICES_PREFIX + "index.json", objectMapper.writeValueAsBytes(index));
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize practice index.json: " + e.getMessage());
        }

        StringBuilder bundle = new StringBuilder();
        for (Practice p : practices) {
            String criteria = p.getCriteria();
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

    /** The lifecycle trigger event stored on the job by the handler, or {@code null} if absent. */
    @Nullable
    private static String triggerEventOf(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return null;
        }
        JsonNode node = metadata.get("trigger_event");
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String event = node.asString();
        return (event == null || event.isBlank()) ? null : event;
    }

    /** True iff the practice's {@code triggerEvents} JSONB array contains {@code event} (gate semantics). */
    private static boolean containsTriggerEvent(@Nullable JsonNode triggerEvents, String event) {
        if (triggerEvents == null || !triggerEvents.isArray()) {
            return false;
        }
        for (JsonNode n : triggerEvents) {
            if (event.equals(n.asString())) {
                return true;
            }
        }
        return false;
    }
}

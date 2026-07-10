package de.tum.cit.aet.hephaestus.practices.report.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One piece of feedback on the reflective dashboard — a single observation rendered for a developer to READ:
 * the headline, the actionable guidance, where it is, and a handle to open the full observation. Deliberately
 * NOT the raw {@link Observation}: no observation enum, no reasoning machinery, and never any criteria — the
 * dashboard is a learner surface, so it carries only what helps the developer act.
 */
@Schema(description = "A single piece of practice feedback to read and act on")
public record PracticeReportItemDTO(
    @NonNull @Schema(description = "Observation id — handle to open the full detail") UUID observationId,
    @NonNull @Schema(description = "The headline of the feedback") String title,
    @Nullable
    @Schema(description = "What to do — the delivered feedback for this observation (null if nothing was delivered)")
    String guidance,
    @Nullable @Schema(description = "Impact level (null unless assessed BAD)") Severity severity,
    @NonNull @Schema(description = "The kind of work this is about (PR / issue)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Id of the PR / issue this is about") Long artifactId,
    @Nullable @Schema(description = "Where in the work, e.g. \"FrameRecorder.swift:212\", when known") String locator
) {
    public static PracticeReportItemDTO from(Observation observation, @Nullable String deliveredGuidance) {
        return new PracticeReportItemDTO(
            observation.getId(),
            observation.getTitle(),
            deliveredGuidance,
            observation.getSeverity(),
            observation.getArtifactType(),
            observation.getArtifactId(),
            locatorOf(observation.getEvidence())
        );
    }

    /**
     * Best-effort "path:line" from {@code evidence.locations[0]}; null when there is no file location (many
     * practices — PR description quality, issue clarity — have none). Never throws on a shape it doesn't
     * recognise.
     */
    private static @Nullable String locatorOf(@Nullable JsonNode evidence) {
        if (evidence == null) {
            return null;
        }
        JsonNode locations = evidence.get("locations");
        if (locations == null || !locations.isArray() || locations.isEmpty()) {
            return null;
        }
        JsonNode first = locations.get(0);
        if (first == null) {
            return null;
        }
        JsonNode path = first.get("path");
        if (path == null || !path.isTextual() || path.asString().isBlank()) {
            return null;
        }
        // Strip the repo-mount prefix FIRST so a genuine repo file under inputs/sources/scm/repo/<file> renders
        // as an openable repo-relative path — and so the internal-path check below is not fooled by the shared
        // "inputs/" prefix (C2): the mount lives under inputs/ too, but it is real user code, not plumbing.
        String p = repoRelative(path.asString());
        // The detector sometimes cites its OWN context/precompute file as the "location" (e.g.
        // inputs/context/test_presence.json, inputs/practices/*, manifest.json). Those are agent-internal
        // plumbing, never something a developer can open — omit the locator rather than leak it.
        if (isInternalContextPath(p)) {
            return null;
        }
        JsonNode startLine = first.get("startLine");
        return (startLine != null && startLine.isNumber()) ? p + ":" + startLine.asInt() : p;
    }

    // Workspace ABI prefixes (ADR 0020), mirrored locally to avoid a practices -> agent module dependency.
    // The repo checkout mounts at REPO_MOUNT_RELATIVE; the agent-internal artifacts live under context/,
    // practices/, and the input manifest. WorkspaceAbi is the source of truth for these literals.
    private static final String REPO_MOUNT_RELATIVE = "inputs/sources/scm/repo/";
    private static final String CONTEXT_PREFIX = "inputs/context/";
    private static final String PRACTICES_PREFIX = "inputs/practices/";
    private static final String MANIFEST_PATH = "inputs/manifest.json";

    /** Strips the integration-namespaced repo-mount prefix ({@code inputs/sources/scm/repo/}, ADR 0020). */
    private static String repoRelative(String path) {
        return path.startsWith(REPO_MOUNT_RELATIVE) ? path.substring(REPO_MOUNT_RELATIVE.length()) : path;
    }

    /**
     * True only for genuinely agent-internal artifacts (context / practices / the input manifest), NEVER for
     * real repo code. The path is already repo-relativized by {@link #locatorOf}, so a real file no longer
     * carries the {@code inputs/sources/...} mount prefix and is correctly classified as student code.
     */
    private static boolean isInternalContextPath(String path) {
        if (path.startsWith(CONTEXT_PREFIX) || path.startsWith(PRACTICES_PREFIX) || path.equals(MANIFEST_PATH)) {
            return true;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        // Known agent-context basenames that can appear without the directory prefix (the agent strips it).
        return (
            lower.equals("manifest.json") ||
            lower.endsWith("/manifest.json") ||
            lower.endsWith("metadata.json") ||
            lower.endsWith("test_presence.json") ||
            lower.endsWith("linked_work_items.json") ||
            lower.endsWith("review_threads.json") ||
            lower.endsWith("comments.json") ||
            lower.endsWith("diff_summary.md") ||
            lower.endsWith("diff_stat.txt")
        );
    }
}

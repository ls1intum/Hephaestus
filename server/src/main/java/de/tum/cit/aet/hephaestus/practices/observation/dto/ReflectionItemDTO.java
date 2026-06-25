package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One piece of feedback on the reflective dashboard — a single finding rendered for a developer to READ:
 * the headline, the actionable guidance, where it is, and a handle to open the full finding. Deliberately
 * NOT the raw {@link Observation}: no observation enum, no reasoning machinery, and never any criteria — the
 * dashboard is a learner surface, so it carries only what helps the developer act.
 */
@Schema(description = "A single piece of practice feedback to read and act on")
public record ReflectionItemDTO(
    @NonNull @Schema(description = "Finding id — handle to open the full detail") UUID findingId,
    @NonNull @Schema(description = "The headline of the feedback") String title,
    @Nullable
    @Schema(description = "What to do — the delivered feedback for this finding (null if nothing was delivered)")
    String guidance,
    @NonNull @Schema(description = "Impact level") Severity severity,
    @NonNull @Schema(description = "The kind of work this is about (PR / issue)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Id of the PR / issue this is about") Long artifactId,
    @Nullable @Schema(description = "Where in the work, e.g. \"FrameRecorder.swift:212\", when known") String locator
) {
    public static ReflectionItemDTO from(Observation f, @Nullable String deliveredGuidance) {
        return new ReflectionItemDTO(
            f.getId(),
            f.getTitle(),
            deliveredGuidance,
            f.getSeverity(),
            f.getArtifactType(),
            f.getArtifactId(),
            locatorOf(f.getEvidence())
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
        String p = path.asString();
        // The detector sometimes cites its OWN context/precompute file as the "location" (e.g.
        // inputs/context/test_presence.json, context/target/review_threads.json, metadata.json). Those are
        // agent-internal plumbing, never something a developer can open — omit the locator rather than leak it.
        if (isInternalContextPath(p)) {
            return null;
        }
        JsonNode startLine = first.get("startLine");
        return (startLine != null && startLine.isNumber()) ? p + ":" + startLine.asInt() : p;
    }

    private static boolean isInternalContextPath(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("inputs/") || lower.startsWith("context/") || lower.startsWith("/inputs/")) {
            return true;
        }
        // Known agent-context basenames that can appear without the directory prefix.
        return (
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

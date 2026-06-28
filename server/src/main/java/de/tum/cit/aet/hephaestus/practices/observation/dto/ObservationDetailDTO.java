package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Detail-view DTO for a single practice observation. Includes guidance, reasoning,
 * and structured evidence that are omitted from the list view.
 *
 * <p>Intentionally omits internal fields: {@code agentJobId}, {@code occurrenceKey},
 * and raw {@code aboutUserId}.
 */
@Schema(description = "Full practice observation detail including guidance and evidence")
public record ObservationDetailDTO(
    @NonNull @Schema(description = "Observation ID") UUID id,
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @NonNull @Schema(description = "Target type (e.g. PULL_REQUEST)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Target entity ID") Long artifactId,
    @NonNull @Schema(description = "Observation title") String title,
    @NonNull @Schema(description = "Presence: PRESENT, ABSENT, or NOT_APPLICABLE") Presence presence,
    @Nullable @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Nullable @Schema(description = "Severity level (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @Nullable
    @Schema(
        description = "Structured evidence: {\"locations\":[{\"path\",\"startLine\",\"endLine\"}], \"snippets\":[...], \"references\":[...]}"
    )
    Map<String, Object> evidence,
    @Nullable @Schema(description = "AI reasoning behind the observation") String reasoning,
    @Nullable
    @Schema(description = "What to do — the delivered feedback for this observation (null if nothing was delivered)")
    String guidance,
    @NonNull @Schema(description = "When the observation was made") Instant observedAt
) {
    public static ObservationDetailDTO from(
        Observation f,
        @Nullable String deliveredGuidance,
        tools.jackson.databind.ObjectMapper mapper
    ) {
        var practice = f.getPractice();
        return new ObservationDetailDTO(
            f.getId(),
            practice.getSlug(),
            practice.getName(),
            f.getArtifactType(),
            f.getArtifactId(),
            f.getTitle(),
            f.getPresence(),
            f.getAssessment(),
            f.getSeverity(),
            f.getConfidence(),
            toMap(f.getEvidence(), mapper),
            f.getReasoning(),
            deliveredGuidance,
            f.getObservedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node, tools.jackson.databind.ObjectMapper mapper) {
        if (node == null) {
            return null;
        }
        return mapper.convertValue(node, Map.class);
    }
}

package de.tum.cit.aet.hephaestus.practices.finding.dto;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * List-view DTO for practice findings. Omits large text fields (guidance, reasoning)
 * and internal fields (agentJobId, idempotencyKey, evidence) to keep payloads small.
 */
@Schema(description = "Practice finding summary for list views")
public record PracticeFindingListDTO(
    @NonNull @Schema(description = "Finding ID") UUID id,
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @NonNull @Schema(description = "Target type (e.g. PULL_REQUEST)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Target entity ID") Long artifactId,
    @NonNull @Schema(description = "Finding title") String title,
    @NonNull @Schema(description = "Observation: OBSERVED, NOT_OBSERVED, or NOT_APPLICABLE") Observation verdict,
    @NonNull @Schema(description = "Severity level") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @NonNull @Schema(description = "When the finding was detected") Instant detectedAt
) {
    /**
     * Maps a {@link PracticeFinding} entity (with eagerly fetched practice) to a list DTO.
     */
    public static PracticeFindingListDTO from(PracticeFinding f) {
        var practice = f.getPractice();
        return new PracticeFindingListDTO(
            f.getId(),
            practice.getSlug(),
            practice.getName(),
            f.getArtifactType(),
            f.getArtifactId(),
            f.getTitle(),
            f.getVerdict(),
            f.getSeverity(),
            f.getConfidence(),
            f.getDetectedAt()
        );
    }
}

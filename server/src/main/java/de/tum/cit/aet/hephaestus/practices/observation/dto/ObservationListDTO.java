package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * List-view DTO for practice observations. Omits large text fields (guidance, reasoning)
 * and internal fields (agentJobId, occurrenceKey, evidence) to keep payloads small.
 */
@Schema(description = "Practice observation summary for list views")
public record ObservationListDTO(
    @NonNull @Schema(description = "Observation ID") UUID id,
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @NonNull @Schema(description = "Artifact type (e.g. PULL_REQUEST)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Artifact entity ID") Long artifactId,
    @NonNull @Schema(description = "Observation title") String title,
    @NonNull @Schema(description = "Presence: PRESENT, ABSENT, or NOT_APPLICABLE") Presence presence,
    @Nullable @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Nullable @Schema(description = "Severity level (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @NonNull @Schema(description = "When the observation was made") Instant observedAt
) {
    /**
     * Maps a {@link Observation} entity (with eagerly fetched practice) to a list DTO.
     */
    public static ObservationListDTO from(Observation observation) {
        var practice = observation.getPractice();
        return new ObservationListDTO(
            observation.getId(),
            practice.getSlug(),
            practice.getName(),
            observation.getArtifactType(),
            observation.getArtifactId(),
            observation.getTitle(),
            observation.getPresence(),
            observation.getAssessment(),
            observation.getSeverity(),
            observation.getConfidence(),
            observation.getObservedAt()
        );
    }
}

package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
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
    @NonNull @Schema(description = "Artifact type (e.g. PULL_REQUEST)") WorkArtifact artifactType,
    @NonNull @Schema(description = "Artifact entity ID") Long artifactId,
    @NonNull @Schema(description = "Observation title") String title,
    @NonNull @Schema(description = "Presence: PRESENT, ABSENT, or NOT_APPLICABLE") Presence presence,
    @Nullable @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Nullable @Schema(description = "Severity level (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @Nullable ObservationEvidenceDTO evidence,
    @Nullable @Schema(description = "AI reasoning behind the observation") String reasoning,
    @Nullable
    @Schema(description = "What to do — the delivered feedback for this observation (null if nothing was delivered)")
    String guidance,
    @NonNull ReviewClaimCurrentness claimCurrentness,
    @NonNull @Schema(description = "When the observation was made") Instant observedAt
) {
    public static ObservationDetailDTO from(
        Observation observation,
        @Nullable String deliveredGuidance,
        boolean includeEvidence
    ) {
        var practice = observation.getPractice();
        return new ObservationDetailDTO(
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
            includeEvidence ? ObservationEvidenceDTO.from(observation.getEvidence()) : null,
            observation.getReasoning(),
            deliveredGuidance,
            ReviewClaimCurrentness.of(observation.getPracticeRevision(), practice),
            observation.getObservedAt()
        );
    }
}

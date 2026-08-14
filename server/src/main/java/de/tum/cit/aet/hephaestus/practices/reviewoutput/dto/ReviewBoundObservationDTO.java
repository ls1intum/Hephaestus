package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.BoundObservation;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "An observation that contributed to a piece of feedback")
public record ReviewBoundObservationDTO(
    @NonNull UUID observationId,
    @NonNull @Schema(description = "Whether the observation leads the feedback or reinforces it") EvidenceRole role,
    @NonNull @Schema(description = "Render order within the feedback (lower renders earlier)") Integer ordinal,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Practice area; null when the practice is Unassigned") ReviewPracticeAreaDTO area,
    @NonNull String title,
    @NonNull Presence presence,
    @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Schema(description = "Severity band (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "Observation confidence", minimum = "0", maximum = "1") Float confidence,
    @NonNull ReviewClaimCurrentness claimCurrentness,
    @NonNull Instant observedAt
) {
    public static ReviewBoundObservationDTO from(BoundObservation row) {
        return new ReviewBoundObservationDTO(
            row.getObservationId(),
            row.getRole(),
            row.getOrdinal(),
            row.getPracticeSlug(),
            row.getPracticeName(),
            ReviewPracticeAreaDTO.from(row.getAreaSlug(), row.getAreaName(), row.getAreaIcon(), row.getAreaColor()),
            row.getTitle(),
            row.getPresence(),
            row.getAssessment(),
            row.getSeverity(),
            row.getConfidence(),
            ReviewClaimCurrentness.of(
                row.getPracticeRevisionFingerprint(),
                row.getCurrentPracticeRevisionFingerprint()
            ),
            row.getObservedAt()
        );
    }
}

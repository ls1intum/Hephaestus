package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.AssessmentClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.BoundObservation;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A finding that contributed to a message")
public record ReviewBoundFindingDTO(
    @NonNull UUID findingId,
    @NonNull @Schema(description = "Whether the finding leads the message or reinforces it") EvidenceRole role,
    @NonNull @Schema(description = "Render order within the message (lower renders earlier)") Integer ordinal,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Practice area; null when the practice is Unassigned") ReviewPracticeAreaDTO area,
    @NonNull String title,
    @NonNull Presence presence,
    @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Schema(description = "Severity band (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "Finding confidence", minimum = "0", maximum = "1") Float confidence,
    @NonNull AssessmentClaimCurrentness claimCurrentness,
    @NonNull Instant observedAt
) {
    public static ReviewBoundFindingDTO from(BoundObservation row) {
        return new ReviewBoundFindingDTO(
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
            AssessmentClaimCurrentness.of(
                row.getPracticeRevisionFingerprint(),
                row.getCurrentPracticeRevisionFingerprint()
            ),
            row.getObservedAt()
        );
    }
}

package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ObservationFeedbackDisposition;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.OperatorObservationRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A practice review observation with its linked feedback outcomes")
public record ReviewObservationDTO(
    @NonNull UUID id,
    @NonNull UUID agentJobId,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Practice area; null when the practice is Unassigned") ReviewPracticeAreaDTO area,
    @NonNull ReviewArtifactDTO artifact,
    @Schema(description = "Whose work the observation is about; null when the identity is no longer resolvable")
    ReviewSubjectDTO subject,
    @NonNull String title,
    @NonNull Presence presence,
    @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Schema(description = "Severity band (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "Observation confidence", minimum = "0", maximum = "1") Float confidence,
    @Schema(description = "Cross-run locus key; null when continuity is unavailable") String recurrenceKey,
    @NonNull
    @Schema(
        description = "What occasioned the measurement. BACKFILL came from a confirmed campaign over work " +
            "that already existed, so it is not a point on the live trend line."
    )
    ObservationOrigin origin,
    @NonNull ReviewClaimCurrentness claimCurrentness,
    @NonNull Instant observedAt,
    @NonNull
    @Schema(description = "Counts of linked feedback by delivery state")
    ReviewFeedbackDispositionDTO feedbackDisposition
) {
    public static ReviewObservationDTO from(
        OperatorObservationRow row,
        ObservationFeedbackDisposition disposition,
        ReviewArtifactDTO artifact,
        Map<Long, ReviewSubjectDTO> subjects
    ) {
        return new ReviewObservationDTO(
            row.getId(),
            row.getAgentJobId(),
            row.getPracticeSlug(),
            row.getPracticeName(),
            ReviewPracticeAreaDTO.from(row.getAreaSlug(), row.getAreaName(), row.getAreaIcon(), row.getAreaColor()),
            artifact,
            subjects.get(row.getAboutUserId()),
            row.getTitle(),
            row.getPresence(),
            row.getAssessment(),
            row.getSeverity(),
            row.getConfidence(),
            row.getRecurrenceKey(),
            row.getOrigin(),
            ReviewClaimCurrentness.of(
                row.getPracticeRevisionFingerprint(),
                row.getCurrentPracticeRevisionFingerprint()
            ),
            row.getObservedAt(),
            disposition == null ? ReviewFeedbackDispositionDTO.empty() : ReviewFeedbackDispositionDTO.from(disposition)
        );
    }
}

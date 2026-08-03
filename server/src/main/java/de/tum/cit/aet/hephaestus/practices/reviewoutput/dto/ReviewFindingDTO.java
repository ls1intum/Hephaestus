package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.EvaluationClaimStatus;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ObservationFeedbackDisposition;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.OperatorObservationRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A practice review finding with its linked feedback outcomes")
public record ReviewFindingDTO(
    @NonNull UUID id,
    @NonNull UUID agentJobId,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Practice area; null when the practice is Unassigned") ReviewPracticeAreaDTO area,
    @NonNull ReviewArtifactDTO artifact,
    @Schema(description = "Whose work the finding is about; null when the identity is no longer resolvable")
    ReviewSubjectDTO subject,
    @NonNull String title,
    @NonNull Presence presence,
    @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Schema(description = "Severity band (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "Detector confidence", minimum = "0", maximum = "1") Float confidence,
    @Schema(description = "Cross-run locus key; null when continuity is unavailable") String recurrenceKey,
    @NonNull EvaluationClaimStatus claimStatus,
    @NonNull Instant observedAt,
    @NonNull
    @Schema(description = "Counts of linked messages by delivery state")
    ReviewFeedbackDispositionDTO feedbackDisposition
) {
    public static ReviewFindingDTO from(
        OperatorObservationRow row,
        ObservationFeedbackDisposition disposition,
        ReviewArtifactDTO artifact,
        Map<Long, ReviewSubjectDTO> subjects
    ) {
        return new ReviewFindingDTO(
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
            EvaluationClaimStatus.of(row.getPracticeRevisionId(), row.getCurrentPracticeRevisionId()),
            row.getObservedAt(),
            disposition == null ? ReviewFeedbackDispositionDTO.empty() : ReviewFeedbackDispositionDTO.from(disposition)
        );
    }
}

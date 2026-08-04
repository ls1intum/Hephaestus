package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.EvaluationClaimStatus;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ObservationEvidenceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A finding with evidence and linked feedback")
public record ReviewFindingDetailDTO(
    @NonNull UUID id,
    @NonNull UUID agentJobId,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Practice area; null when the practice is Unassigned") ReviewPracticeAreaDTO area,
    @Schema(description = "Criteria revision selected as of job start, when available") Long practiceRevisionId,
    @NonNull ReviewArtifactDTO artifact,
    @Schema(description = "Whose work the finding is about; null when the identity is no longer resolvable")
    ReviewSubjectDTO subject,
    @NonNull String title,
    @NonNull Presence presence,
    @Schema(description = "Assessment: GOOD or BAD (null when NOT_APPLICABLE)") Assessment assessment,
    @Schema(description = "Severity band (null unless assessment is BAD)") Severity severity,
    @NonNull @Schema(description = "Detector confidence", minimum = "0", maximum = "1") Float confidence,
    ObservationEvidenceDTO evidence,
    String reasoning,
    @Schema(description = "Cross-run locus key; null when continuity is unavailable") String recurrenceKey,
    @NonNull EvaluationClaimStatus claimStatus,
    @NonNull Instant observedAt,
    @NonNull @Schema(description = "Linked feedback, newest first") List<ReviewBoundFeedbackDTO> feedback
) {
    public static ReviewFindingDetailDTO from(
        Observation observation,
        ReviewArtifactDTO artifact,
        ReviewSubjectDTO subject,
        List<ReviewBoundFeedbackDTO> feedback,
        boolean includeEvidence
    ) {
        var practice = observation.getPractice();
        var revision = observation.getPracticeRevision();
        return new ReviewFindingDetailDTO(
            observation.getId(),
            observation.getAgentJobId(),
            practice.getSlug(),
            practice.getName(),
            practice.getArea() == null ? null : ReviewPracticeAreaDTO.from(practice.getArea()),
            revision == null ? null : revision.getId(),
            artifact,
            subject,
            observation.getTitle(),
            observation.getPresence(),
            observation.getAssessment(),
            observation.getSeverity(),
            observation.getConfidence(),
            includeEvidence ? ObservationEvidenceDTO.from(observation.getEvidence()) : null,
            observation.getReasoning(),
            observation.getRecurrenceKey(),
            EvaluationClaimStatus.of(revision, practice),
            observation.getObservedAt(),
            feedback
        );
    }
}

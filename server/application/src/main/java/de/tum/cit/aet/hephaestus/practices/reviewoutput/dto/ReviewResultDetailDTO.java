package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.dto.ObservationDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultCoverageDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultEvidenceDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultExampleDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultProvenanceDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A result with evidence and linked feedback")
public record ReviewResultDetailDTO(
        @NonNull UUID id,
        @NonNull UUID agentJobId,
        @NonNull String practiceSlug,
        @NonNull String practiceName,

        @Schema(description = "Practice group; null when the practice is Unassigned") @Nullable
        ReviewPracticeGroupDTO group,

        @Schema(description = "Criteria revision selected as of job start, when available") @Nullable
        Long practiceRevisionId,

        @NonNull ReviewedWorkRefDTO reviewedWork,

        @Schema(description = "Whose work the result is about; null when the identity is no longer resolvable")
        @Nullable
        ReviewSubjectDTO subject,

        @NonNull String summary,

        @NonNull @Schema(description = "Result of the practice review")
        Outcome outcome,

        @Schema(description = "Severity band (null unless outcome is NOT_MET)") @Nullable
        Severity severity,

        @NonNull
        @Schema(
                description =
                        "How the review that recorded it came about: LIVE from the work itself, MANUAL requested by a"
                                + " person, BACKFILL over work that predates the connection. A recurring sweep records"
                                + " LIVE.")
        ResultOrigin origin,

        @Schema(
                description = "Whether access to the result's evidence is withheld",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean evidenceHidden,

        @Nullable PracticeResultEvidenceDTO evidence,
        @Nullable String evidenceRationale,

        @NonNull
        @Schema(description = "The located problems of a NOT_MET result, in display order; empty for any other outcome")
        List<ObservationDTO> observations,

        @Schema(description = "One place of the work that shows the practice done well; null when none") @Nullable
        PracticeResultExampleDTO example,

        @Schema(description = "How much of its scope the review read; null for a result recorded before coverage")
        @Nullable
        PracticeResultCoverageDTO coverage,

        @Schema(description = "Where the result came from: job, attempt, revision and program; null before it was kept")
        @Nullable
        PracticeResultProvenanceDTO provenance,

        @NonNull ReviewClaimCurrentness claimCurrentness,

        @Schema(description = "When a later review of the same work superseded this result; null while current")
        @Nullable
        Instant supersededAt,

        @NonNull
        @Schema(
                description = "Every correction by a workspace admin, newest first; the first is in force while it "
                        + "has no restoration")
        List<PracticeResultInvalidationDTO> invalidations,

        @NonNull
        @Schema(
                description = "The developer's standing disputes of feedback written from this result, newest"
                        + " first; empty when nothing about it is disputed")
        List<FeedbackDisputeDTO> disputes,

        @NonNull Instant observedAt,

        @NonNull @Schema(description = "Linked feedback, newest first")
        List<ReviewBoundFeedbackDTO> feedback) {

    public static ReviewResultDetailDTO from(
            PracticeResult practiceResult,
            ReviewedWorkRefDTO reviewedWork,
            @Nullable ReviewSubjectDTO subject,
            List<ReviewBoundFeedbackDTO> feedback,
            List<PracticeResultInvalidationDTO> invalidations,
            List<FeedbackDisputeDTO> disputes,
            boolean includeEvidence,
            List<Observation> problems) {
        var practice = practiceResult.getPractice();
        var revision = practiceResult.getPracticeRevision();
        return new ReviewResultDetailDTO(
                practiceResult.getId(),
                practiceResult.getAgentJobId(),
                practice.getSlug(),
                practice.getName(),
                practice.getGroup() == null ? null : ReviewPracticeGroupDTO.from(practice.getGroup()),
                revision == null ? null : revision.getId(),
                reviewedWork,
                subject,
                practiceResult.getSummary(),
                practiceResult.getOutcome(),
                practiceResult.getSeverity(),
                practiceResult.getOrigin(),
                !includeEvidence,
                includeEvidence ? PracticeResultEvidenceDTO.from(practiceResult.getEvidence(), problems) : null,
                practiceResult.getEvidenceRationale(),
                includeEvidence ? problems.stream().map(ObservationDTO::from).toList() : List.of(),
                includeEvidence ? PracticeResultExampleDTO.from(practiceResult.getExample()) : null,
                PracticeResultCoverageDTO.from(practiceResult.getCoverage()),
                PracticeResultProvenanceDTO.from(practiceResult.getProvenance()),
                ReviewClaimCurrentness.of(revision, practice, practiceResult.getSupersededAt()),
                practiceResult.getSupersededAt(),
                invalidations,
                disputes,
                practiceResult.getObservedAt(),
                feedback);
    }
}

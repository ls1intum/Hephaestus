package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository.OperatorObservationRow;
import de.tum.cit.aet.hephaestus.practices.result.dto.EvidenceCitationSide;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One observation of a not met result, as the admin list names it")
public record PracticeReviewObservationRowDTO(
        @NonNull @Schema(description = "Observation ID") UUID id,

        @NonNull UUID practiceResultId,
        @NonNull String practiceSlug,
        @NonNull String practiceName,

        @Schema(description = "Practice group; null when the practice is Unassigned") @Nullable
        ReviewPracticeGroupDTO group,

        @NonNull @Schema(description = "How much this observation weighs at its place alone")
        Severity severity,

        @Schema(description = "One line; null only when evidenceHidden is true") @Nullable
        String summary,

        @Schema(description = "The first citation's source kind; null when absent or when evidenceHidden is true")
        @Nullable
        String sourceKind,

        @Schema(description = "The first citation's diff side; null when absent or when evidenceHidden is true")
        @Nullable
        EvidenceCitationSide side,

        @Schema(
                description = "The path of the observation's first citation; null when it names none or when"
                        + " evidenceHidden is true")
        @Nullable
        String path,

        @Schema(
                description = "The first line the first citation quotes; null when it gives none or when"
                        + " evidenceHidden is true")
        @Nullable
        Integer startLine,

        @Schema(
                description = "The last line the first citation quotes; null when it gives none or when"
                        + " evidenceHidden is true")
        @Nullable
        Integer endLine,

        @Schema(
                description = "true when the result's detail hides its evidence, so the summary and the place"
                        + " are left out",
                requiredMode = RequiredMode.REQUIRED)
        boolean evidenceHidden,

        @NonNull ReviewClaimCurrentness claimCurrentness,

        @Schema(description = "When a workspace admin invalidated this result; null while it stands") @Nullable
        Instant invalidatedAt,

        @Schema(
                description = "When the developer last disputed feedback written from this result; null while"
                        + " nothing about it is disputed")
        @Nullable
        Instant disputedAt,

        @NonNull ReviewedWorkRefDTO reviewedWork,

        @Schema(description = "Whose work the result is about; null when the identity is no longer resolvable")
        @Nullable
        ReviewSubjectDTO subject,

        @NonNull @Schema(description = "When the result was recorded")
        Instant observedAt,

        @NonNull @Schema(description = "The review that recorded the result")
        UUID agentJobId) {

    /** The row as the list shows it; with {@code evidenceShown} false, nothing the evidence says comes with it. */
    public static PracticeReviewObservationRowDTO from(
            OperatorObservationRow row,
            boolean evidenceShown,
            ReviewedWorkRefDTO reviewedWork,
            Map<Long, ReviewSubjectDTO> subjects) {
        return new PracticeReviewObservationRowDTO(
                row.getId(),
                row.getPracticeResultId(),
                row.getPracticeSlug(),
                row.getPracticeName(),
                ReviewPracticeGroupDTO.from(
                        row.getGroupSlug(), row.getGroupName(), row.getGroupIcon(), row.getGroupColor()),
                row.getSeverity(),
                evidenceShown ? row.getSummary() : null,
                evidenceShown ? row.getSourceKind() : null,
                evidenceShown ? row.getSide() : null,
                evidenceShown ? row.getPath() : null,
                evidenceShown ? row.getStartLine() : null,
                evidenceShown ? row.getEndLine() : null,
                !evidenceShown,
                ReviewClaimCurrentness.of(
                        row.getPracticeRevisionFingerprint(),
                        row.getCurrentPracticeRevisionFingerprint(),
                        row.getSupersededAt()),
                row.getInvalidatedAt(),
                row.getDisputedAt(),
                reviewedWork,
                row.getAboutUserId() == null ? null : subjects.get(row.getAboutUserId()),
                row.getObservedAt(),
                row.getAgentJobId());
    }
}

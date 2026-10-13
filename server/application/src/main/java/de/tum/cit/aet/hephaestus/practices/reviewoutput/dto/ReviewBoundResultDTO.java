package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository.BoundResult;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A result that contributed to a piece of feedback")
public record ReviewBoundResultDTO(
        @NonNull UUID practiceResultId,

        @NonNull @Schema(description = "Whether the result leads the feedback or reinforces it")
        EvidenceRole role,

        @NonNull @Schema(description = "Render order within the feedback (lower renders earlier)")
        Integer ordinal,

        @NonNull String practiceSlug,
        @NonNull String practiceName,

        @Schema(description = "Practice group; null when the practice is Unassigned") @Nullable
        ReviewPracticeGroupDTO group,

        @Schema(description = "Result text; absent when source access cannot be verified") @Nullable
        String summary,

        @NonNull @Schema(description = "Result of the practice review")
        Outcome outcome,

        @Schema(description = "Severity band (null unless outcome is NOT_MET)") @Nullable
        Severity severity,

        @NonNull ReviewClaimCurrentness claimCurrentness,
        @NonNull Instant observedAt) {

    public static ReviewBoundResultDTO from(BoundResult row, boolean includeWords) {
        return new ReviewBoundResultDTO(
                row.getPracticeResultId(),
                row.getRole(),
                row.getOrdinal(),
                row.getPracticeSlug(),
                row.getPracticeName(),
                ReviewPracticeGroupDTO.from(
                        row.getGroupSlug(), row.getGroupName(), row.getGroupIcon(), row.getGroupColor()),
                includeWords ? row.getSummary() : null,
                row.getOutcome(),
                row.getSeverity(),
                ReviewClaimCurrentness.of(
                        row.getPracticeRevisionFingerprint(),
                        row.getCurrentPracticeRevisionFingerprint(),
                        row.getSupersededAt()),
                row.getObservedAt());
    }
}

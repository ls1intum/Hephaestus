package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.FeedbackStateCounts;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.OperatorResultRow;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A practice review result with its linked feedback outcomes")
public record ReviewResultDTO(
        @NonNull UUID id,
        @NonNull UUID agentJobId,
        @NonNull String practiceSlug,
        @NonNull String practiceName,

        @Schema(description = "Practice group; null when the practice is Unassigned") @Nullable
        ReviewPracticeGroupDTO group,

        @NonNull ReviewedWorkRefDTO reviewedWork,

        @Schema(description = "Whose work the result is about; null when the identity is no longer resolvable")
        @Nullable
        ReviewSubjectDTO subject,

        @Schema(description = "Result text; absent when source access cannot be verified") @Nullable
        String summary,

        @NonNull @Schema(description = "Result of the practice review")
        Outcome outcome,

        @Schema(description = "Severity band (null unless outcome is NOT_MET)") @Nullable
        Severity severity,

        @Schema(
                description = "The located problems of the result; 0 for every outcome but NOT_MET",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long problemCount,

        @NonNull
        @Schema(
                description = "What occasioned the measurement. BACKFILL came from a confirmed campaign over work "
                        + "that already existed, so it is not a point on the live trend line.")
        ResultOrigin origin,

        @NonNull ReviewClaimCurrentness claimCurrentness,

        @Schema(description = "When a workspace admin invalidated this result; null while it stands") @Nullable
        Instant invalidatedAt,

        @Schema(
                description = "When the developer last disputed feedback written from this result; null while"
                        + " nothing about it is disputed")
        @Nullable
        Instant disputedAt,

        @NonNull Instant observedAt,

        @NonNull @Schema(description = "Counts of linked feedback by delivery state")
        ReviewFeedbackCountsDTO feedback) {

    public static ReviewResultDTO from(
            OperatorResultRow row,
            @Nullable FeedbackStateCounts feedback,
            ReviewedWorkRefDTO reviewedWork,
            Map<Long, ReviewSubjectDTO> subjects,
            boolean includeWords) {
        return new ReviewResultDTO(
                row.getId(),
                row.getAgentJobId(),
                row.getPracticeSlug(),
                row.getPracticeName(),
                ReviewPracticeGroupDTO.from(
                        row.getGroupSlug(), row.getGroupName(), row.getGroupIcon(), row.getGroupColor()),
                reviewedWork,
                subjects.get(row.getAboutUserId()),
                includeWords ? row.getSummary() : null,
                row.getOutcome(),
                row.getSeverity(),
                row.getProblemCount(),
                row.getOrigin(),
                ReviewClaimCurrentness.of(
                        row.getPracticeRevisionFingerprint(),
                        row.getCurrentPracticeRevisionFingerprint(),
                        row.getSupersededAt()),
                row.getInvalidatedAt(),
                row.getDisputedAt(),
                row.getObservedAt(),
                ReviewFeedbackCountsDTO.from(feedback));
    }
}

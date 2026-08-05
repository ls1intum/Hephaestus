package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Readiness decisions recorded on recent reviews, grouped by practice. */
@Schema(description = "Recent review readiness for one practice's evidence requirements")
public record PracticeEvidenceOutcomeDTO(
    @NonNull @Schema(description = "Practice this outcome describes") String practiceSlug,
    @NonNull @Schema(description = "Reviews that considered this practice") Integer consideredReviews,
    @NonNull @Schema(description = "Reviews where the evidence met every requirement") Integer reviewedCount,
    @NonNull
    @Schema(
        description = "Why the remaining reviews were skipped, most frequent first. Not a partition: " +
            "one review blocked on two sources appears under both, so these can sum above the skipped count."
    )
    List<PracticeEvidenceBlockDTO> skippedBecause
) {
    @Schema(description = "One reason automated review was skipped, and how often")
    public record PracticeEvidenceBlockDTO(
        @Nullable @Schema(
            description = "Source that did not meet its requirement; absent when the practice itself runs no review"
        ) String sourceKind,
        @NonNull @Schema(
            description = "Readiness reason recorded for that source or practice",
            allowableValues = {
                "SOURCE_NOT_AVAILABLE",
                "SOURCE_INCOMPLETE",
                "SOURCE_NOT_CURRENT",
                "SOURCE_EMPTY",
                "NO_AUTOMATED_REVIEW",
                "DECLARED_EVIDENCE_INSUFFICIENT",
            }
        ) String reasonCode,
        @NonNull @Schema(description = "Reviews skipped for this reason") Integer reviews
    ) {}
}

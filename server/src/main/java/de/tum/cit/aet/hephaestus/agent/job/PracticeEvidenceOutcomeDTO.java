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
    @Schema(description = "Blockers seen on the skipped reviews, most frequent first")
    List<PracticeEvidenceBlockerDTO> blockersObserved
) {
    @Schema(description = "One thing that blocked automated review, and how many reviews it affected")
    public record PracticeEvidenceBlockerDTO(
        @Nullable @Schema(
            description = "Source that did not meet its requirement; absent when the practice itself runs no review"
        ) String sourceKind,
        @NonNull @Schema(
            description = "Readiness reason recorded for that source or practice"
        ) PracticeEvidenceSkipReason reasonCode,
        @NonNull @Schema(description = "Reviews this blocker affected") Integer reviewsAffected
    ) {}
}

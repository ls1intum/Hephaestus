package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * How a practice's evidence requirements have actually turned out on recent reviews.
 *
 * <p>An author sets requirements against an idea of what the sources usually hold. Nothing has told
 * them whether that idea is right: a requirement that quietly skips four reviews in five looks
 * identical, in the editor, to one that never skips at all. Every run already records a readiness
 * decision per practice; this is that record read back.
 */
@Schema(description = "Recent review readiness for one practice's evidence requirements")
public record PracticeEvidenceOutcomeDTO(
    @NonNull @Schema(description = "Practice this outcome describes") String practiceSlug,
    @NonNull @Schema(description = "Reviews that considered this practice") Integer consideredReviews,
    @NonNull @Schema(description = "Reviews where the evidence met every requirement") Integer reviewedCount,
    @NonNull
    @Schema(description = "Why the remaining reviews were skipped, most frequent first")
    List<PracticeEvidenceBlockDTO> skippedBecause
) {
    @Schema(description = "One reason automated review was skipped, and how often")
    public record PracticeEvidenceBlockDTO(
        @NonNull @Schema(description = "Source that did not meet its requirement") String sourceKind,
        @NonNull @Schema(description = "Readiness reason recorded for that source") String reasonCode,
        @NonNull @Schema(description = "Reviews skipped for this source and reason") Integer reviews
    ) {}
}

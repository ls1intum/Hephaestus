package de.tum.in.www1.hephaestus.practices.finding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Per-practice aggregation of findings for a contributor. Used by dashboard practice cards
 * to display verdict counts and last activity.
 */
@Schema(description = "Per-practice finding summary for a contributor")
public record ContributorPracticeSummaryDTO(
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @Nullable @Schema(description = "Practice category") String category,
    @NonNull @Schema(description = "Total number of findings") Long totalFindings,
    @NonNull @Schema(description = "Number of POSITIVE findings") Long positiveCount,
    @NonNull @Schema(description = "Number of NEGATIVE findings") Long negativeCount,
    @Nullable @Schema(description = "Timestamp of most recent finding") Instant lastFindingAt
) {
    public static ContributorPracticeSummaryDTO from(ContributorPracticeSummaryProjection p) {
        return new ContributorPracticeSummaryDTO(
            p.getPracticeSlug(),
            p.getPracticeName(),
            p.getCategory(),
            p.getTotalFindings(),
            p.getPositiveCount(),
            p.getNegativeCount(),
            p.getLastFindingAt()
        );
    }
}

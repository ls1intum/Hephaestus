package de.tum.cit.aet.hephaestus.practices.finding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Per-practice aggregation of findings for a developer. Used by dashboard practice cards
 * to display verdict counts and last activity.
 */
@Schema(description = "Per-practice finding summary for a developer")
public record DeveloperPracticeSummaryDTO(
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @NonNull @Schema(description = "Total number of findings") Long totalFindings,
    @NonNull @Schema(description = "Number of OBSERVED findings") Long observedCount,
    @NonNull @Schema(description = "Number of NOT_OBSERVED findings") Long notObservedCount,
    @Nullable @Schema(description = "Timestamp of most recent finding") Instant lastFindingAt
) {
    public static DeveloperPracticeSummaryDTO from(DeveloperPracticeSummaryProjection p) {
        return new DeveloperPracticeSummaryDTO(
            p.getPracticeSlug(),
            p.getPracticeName(),
            p.getTotalFindings(),
            p.getObservedCount(),
            p.getNotObservedCount(),
            p.getLastFindingAt()
        );
    }
}

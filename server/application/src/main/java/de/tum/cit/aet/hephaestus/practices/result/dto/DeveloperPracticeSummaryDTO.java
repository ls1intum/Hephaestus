package de.tum.cit.aet.hephaestus.practices.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Per-practice aggregation of current results for a developer (ADR 0022).
 */
@Schema(description = "Per-practice result summary for a developer")
public record DeveloperPracticeSummaryDTO(
        @NonNull @Schema(description = "Practice slug") String practiceSlug,
        @NonNull @Schema(description = "Practice name") String practiceName,

        @NonNull @Schema(description = "Total number of results")
        Long totalResults,

        @NonNull @Schema(description = "Number of MET results")
        Long met,

        @NonNull @Schema(description = "Number of NOT_MET results")
        Long notMet,

        @NonNull Long notApplicable,
        @NonNull Long undetermined,

        @Nullable @Schema(description = "Timestamp of most recent result")
        Instant lastObservedAt) {
    public static DeveloperPracticeSummaryDTO from(DeveloperPracticeSummaryProjection p) {
        return new DeveloperPracticeSummaryDTO(
                p.getPracticeSlug(),
                p.getPracticeName(),
                p.getTotalResults(),
                p.getMet(),
                p.getNotMet(),
                p.getNotApplicable(),
                p.getUndetermined(),
                p.getLastObservedAt());
    }
}

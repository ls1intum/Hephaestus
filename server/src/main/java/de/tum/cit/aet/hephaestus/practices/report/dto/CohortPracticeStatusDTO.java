package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Cohort-level health for one reviewing practice over the recency window. Counts are suppressed below the
 * k-anonymity threshold or when a non-zero status bucket is smaller than that threshold.
 */
@Schema(description = "Cohort standing distribution for one reviewing practice (k-anonymised, never per-person)")
public record CohortPracticeStatusDTO(
    @NonNull @Schema(description = "Practice slug") String slug,
    @NonNull @Schema(description = "Practice name") String name,
    @Schema(description = "True when suppressed for k-anonymity (< 5 developers active, or a non-zero bucket has < 5)")
    boolean suppressed,
    @Nullable @Schema(description = "Developers standing at STRENGTH (null when suppressed)") Integer strengthCount,
    @Nullable @Schema(description = "Developers standing at DEVELOPING (null when suppressed)") Integer developingCount,
    @Nullable @Schema(description = "Developers standing at MIXED (null when suppressed)") Integer mixedCount,
    @Nullable
    @Schema(description = "Developers with activity but no problems/strengths this window (null when suppressed)")
    Integer noActivityCount
) {
    public static CohortPracticeStatusDTO suppressed(String slug, String name) {
        return new CohortPracticeStatusDTO(slug, name, true, null, null, null, null);
    }
}

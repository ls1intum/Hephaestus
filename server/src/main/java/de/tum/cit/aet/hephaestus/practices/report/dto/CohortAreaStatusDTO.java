package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Cohort-level health for one practice AREA over the recency window (P1: generalised from the single
 * reviewing-practice area to every area). Counts are suppressed below the k-anonymity threshold or when a
 * non-zero status bucket is smaller than that threshold.
 *
 * <p>Distinguishes two very different "no counts" reasons: {@link #noData} means literally nobody was active
 * on this area in the window — there is no one to re-identify, so this is NOT a privacy suppression, just an
 * empty area. {@link #suppressed} means there WAS activity, but too little of it to publish without risking
 * re-identifying an individual. Exactly one of the two may be true; when both are false, the counts are
 * populated.
 */
@Schema(description = "Cohort standing distribution for one practice area (k-anonymised, never per-person)")
public record CohortAreaStatusDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @Schema(description = "True when suppressed for k-anonymity (< 5 developers active, or a non-zero bucket has < 5)")
    boolean suppressed,
    @Schema(
        description = "True when no developer had activity on this area in the window (not a privacy risk — nobody to re-identify)"
    )
    boolean noData,
    @Nullable
    @Schema(description = "Developers standing at STRENGTH (null when suppressed or no data)")
    Integer strengthCount,
    @Nullable
    @Schema(description = "Developers standing at DEVELOPING (null when suppressed or no data)")
    Integer developingCount,
    @Nullable
    @Schema(description = "Developers standing at MIXED (null when suppressed or no data)")
    Integer mixedCount,
    @Nullable
    @Schema(
        description = "Developers with activity but no problems/strengths this window (null when suppressed or no data)"
    )
    Integer noActivityCount
) {
    public static CohortAreaStatusDTO suppressed(String areaSlug, String areaName) {
        return new CohortAreaStatusDTO(areaSlug, areaName, true, false, null, null, null, null);
    }

    public static CohortAreaStatusDTO noData(String areaSlug, String areaName) {
        return new CohortAreaStatusDTO(areaSlug, areaName, false, true, null, null, null, null);
    }
}

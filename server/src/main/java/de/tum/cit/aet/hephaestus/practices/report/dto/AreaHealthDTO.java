package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Workspace health for one practice AREA over the recency window (P1: generalised from the single
 * reviewing-practice area to every area). Counts are suppressed below the k-anonymity threshold or when a
 * non-zero status bucket is smaller than that threshold.
 *
 * <p>{@link HealthAvailability} distinguishes two very different "no counts" reasons: {@code NO_DATA} means
 * literally nobody was active on this area in the window — there is no one to re-identify, so this is NOT a
 * privacy suppression, just an empty area. {@code SUPPRESSED} means there WAS activity, but too little of it
 * to publish without risking re-identifying an individual. {@code AVAILABLE} means the counts are populated.
 * Modeled as one discriminant rather than two booleans, which would have permitted an invalid 4th state.
 */
@Schema(description = "Workspace health distribution for one practice area (k-anonymised, never per-person)")
public record AreaHealthDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(description = "Whether counts are available, suppressed for k-anonymity, or there was no data at all")
    HealthAvailability availability,
    @Nullable
    @Schema(description = "Developers standing at STRENGTH (null unless availability is AVAILABLE)")
    Integer strengthCount,
    @Nullable
    @Schema(description = "Developers standing at DEVELOPING (null unless availability is AVAILABLE)")
    Integer developingCount,
    @Nullable
    @Schema(description = "Developers standing at MIXED (null unless availability is AVAILABLE)")
    Integer mixedCount,
    @Nullable
    @Schema(
        description = "Developers with activity but no problems/strengths this window (null unless availability is AVAILABLE)"
    )
    Integer noActivityCount
) {
    public static AreaHealthDTO suppressed(String areaSlug, String areaName) {
        return new AreaHealthDTO(areaSlug, areaName, HealthAvailability.SUPPRESSED, null, null, null, null);
    }

    public static AreaHealthDTO noData(String areaSlug, String areaName) {
        return new AreaHealthDTO(areaSlug, areaName, HealthAvailability.NO_DATA, null, null, null, null);
    }
}

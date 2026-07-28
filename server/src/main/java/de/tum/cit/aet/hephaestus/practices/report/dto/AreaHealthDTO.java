package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Workspace health for one practice AREA over the report window: how many developers stand at each status.
 * Never per-person, and never ordered — it answers "where does this team need support", not "who is behind".
 *
 * <p>All four counts are null unless {@link #availability} is {@link HealthAvailability#AVAILABLE}; see that
 * enum for what the other two states mean and {@code PracticeReportService} for the anonymity rules that
 * choose between them.
 */
@Schema(description = "Workspace health distribution for one practice area (anonymised, never per-person)")
public record AreaHealthDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(description = "Whether counts are available, suppressed to protect individuals, or absent entirely")
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
        description = "Developers with activity but no problems or strengths this window (null unless availability is AVAILABLE)"
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

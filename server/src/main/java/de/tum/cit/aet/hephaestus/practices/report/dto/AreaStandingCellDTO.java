package de.tum.cit.aet.hephaestus.practices.report.dto;

import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeTrend;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * One cell in a roster row: a developer's criterion-referenced {@link PracticeStatus} on one practice AREA
 * (rolled up across that area's practices) — a triage label against the area's practices, plus the
 * cycle-over-cycle {@link PracticeTrend} against the prior cycle.
 */
@Schema(description = "A developer's standing on one practice area")
public record AreaStandingCellDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(
        description = "Where the developer stands on this area (criterion-referenced, not a rank)",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NO_ACTIVITY" }
    )
    PracticeStatus status,
    @NonNull
    @Schema(
        description = "Direction versus the prior review cycle (criterion-referenced, never a peer comparison)",
        allowableValues = { "IMPROVING", "WORSENING", "STEADY", "NEW" }
    )
    PracticeTrend trend
) {}

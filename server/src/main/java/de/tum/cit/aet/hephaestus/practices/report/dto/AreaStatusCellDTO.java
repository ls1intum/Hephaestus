package de.tum.cit.aet.hephaestus.practices.report.dto;

import de.tum.cit.aet.hephaestus.practices.report.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.report.PracticeTrend;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * One cell in a roster row: a developer's {@link PracticeStatus} on one practice AREA, rolled up across that
 * area's practices, plus the direction against the previous report window.
 *
 * <p>Area grain, not practice grain: a roster with one column per practice stops being legible once a
 * workspace adopts more than a handful. The per-practice detail is the drill-down.
 */
@Schema(description = "A developer's status on one practice area")
public record AreaStatusCellDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(
        description = "Where the developer stands on this area, read against its practices",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NO_ACTIVITY" }
    )
    PracticeStatus status,
    @NonNull
    @Schema(
        description = "Direction versus the previous report window",
        allowableValues = { "IMPROVING", "WORSENING", "STEADY", "NEW" }
    )
    PracticeTrend trend
) {}

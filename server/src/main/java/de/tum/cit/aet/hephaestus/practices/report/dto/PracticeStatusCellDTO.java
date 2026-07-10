package de.tum.cit.aet.hephaestus.practices.report.dto;

import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * One cell in a roster row: a developer's criterion-referenced {@link PracticeStatus} on one reviewing
 * practice — a triage label against the practice standard.
 */
@Schema(description = "A developer's standing on one reviewing practice")
public record PracticeStatusCellDTO(
    @NonNull @Schema(description = "Practice slug") String slug,
    @NonNull @Schema(description = "Practice name") String name,
    @NonNull
    @Schema(
        description = "Where the developer stands on this practice (criterion-referenced, not a rank)",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NO_ACTIVITY" }
    )
    PracticeStatus standing
) {}

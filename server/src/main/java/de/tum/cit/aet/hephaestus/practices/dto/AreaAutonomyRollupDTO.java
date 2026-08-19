package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One area's practice counts per effective autonomy state")
public record AreaAutonomyRollupDTO(
    @Nullable @Schema(description = "Area slug; null groups the practices that belong to no area") String areaSlug,
    @Nullable @Schema(description = "Area name; null for the no-area group") String areaName,
    @NonNull
    @Schema(description = "The autonomy in force for this area, and where it came from")
    AutonomyAssignmentDTO autonomy,
    @NonNull
    @Schema(description = "Practice count per effective autonomy in this area")
    Map<PracticeAutonomy, Integer> counts,
    @NonNull @Schema(description = "Number of practices with an explicit autonomy override") Integer overriddenCount
) {}

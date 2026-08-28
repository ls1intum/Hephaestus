package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One group's practice counts per effective autonomy state")
public record GroupAutonomyRollupDTO(
        @Nullable @Schema(description = "Group slug; null groups the practices that belong to no group")
        String groupSlug,

        @Nullable @Schema(description = "Group name; null for the no-group group")
        String groupName,

        @NonNull @Schema(description = "The autonomy in force for this group, and where it came from")
        AutonomyAssignmentDTO autonomy,

        @NonNull @Schema(description = "Practice count per effective autonomy in this group")
        Map<PracticeAutonomy, Integer> counts,

        @NonNull @Schema(description = "Number of practices with an explicit autonomy override")
        Integer overriddenCount) {}

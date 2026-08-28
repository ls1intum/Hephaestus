package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

@Schema(description = "Practice counts per autonomy state, for the workspace and each group")
public record AutonomyRollupDTO(
        @NonNull @Schema(description = "The workspace-level decision every group and practice falls back to")
        AutonomyAssignmentDTO workspaceDefault,

        @NonNull @Schema(description = "Practice count per effective autonomy across the workspace")
        Map<PracticeAutonomy, Integer> counts,

        @NonNull @Schema(description = "The same counts per group, in catalogue order")
        List<GroupAutonomyRollupDTO> groups) {}

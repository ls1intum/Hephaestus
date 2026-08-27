package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Set how much autonomy the system has here, or clear it back to inherit")
public record UpdatePracticeAutonomyRequestDTO(
        @Nullable @Schema(description = "Local autonomy override. Null inherits from the group or workspace.")
        PracticeAutonomy autonomy) {}

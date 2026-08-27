package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomySource;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.EffectiveAutonomy;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(
        description =
                "The autonomy in force here, whether it was set here or inherited, and the level " + "that decided it")
public record AutonomyAssignmentDTO(
        @NonNull @Schema(description = "The autonomy actually in force")
        PracticeAutonomy effective,

        @Nullable @Schema(description = "The local override, or null when autonomy is inherited")
        PracticeAutonomy override,

        @NonNull @Schema(description = "Which level decided the effective autonomy")
        AutonomySource source,

        @NonNull @Schema(description = "Whether autonomy is inherited")
        Boolean inherited) {
    public static AutonomyAssignmentDTO of(EffectiveAutonomy resolved, @Nullable PracticeAutonomy override) {
        return new AutonomyAssignmentDTO(resolved.autonomy(), override, resolved.source(), override == null);
    }
}

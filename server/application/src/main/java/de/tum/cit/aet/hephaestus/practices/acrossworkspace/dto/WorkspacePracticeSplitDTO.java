package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.StandingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "One practice of a group: how the developers with a standing split, and where the reader is")
public record WorkspacePracticeSplitDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Practice slug")
        String practiceSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Practice name")
        String practiceName,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The reader's current standing in the practice, the one their practice profile shows:"
                        + " the part they are marked in. Absent unless the split counts the reader")
        @Nullable
        PracticeStanding yourStanding,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the reader's verdict was read from, as their practice profile says. Absent"
                        + " without one")
        @Nullable
        StandingBasis yourBasis,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How the developers with a standing split across the practice")
        WorkspaceSplitDTO split) {}

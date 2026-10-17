package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.dto.GroupRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeWorkspaceTileDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        GroupRefDTO group,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int developers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OutcomeVectorDTO outcomes) {}

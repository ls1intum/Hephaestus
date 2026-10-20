package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The middle half of the developers counted: the 25th to the 75th percentile")
public record WorkspaceRangeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The 25th percentile")
        int low,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The 75th percentile")
        int high) {}

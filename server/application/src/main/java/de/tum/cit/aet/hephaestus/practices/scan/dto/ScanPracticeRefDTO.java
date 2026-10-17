package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanPracticeRefDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String practiceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String practiceVersionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reviewDigest) {}

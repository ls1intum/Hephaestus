package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewLineageEdgeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String relation) {}

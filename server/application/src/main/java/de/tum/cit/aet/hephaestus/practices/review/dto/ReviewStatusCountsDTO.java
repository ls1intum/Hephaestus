package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewStatusCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int queued,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int running,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int complete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int partial,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int cancelled) {}

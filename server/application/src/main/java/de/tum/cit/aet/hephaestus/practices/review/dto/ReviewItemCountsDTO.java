package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewItemCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int listed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reused,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheld,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notReviewed) {}

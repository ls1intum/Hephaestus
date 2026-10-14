package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewTimeSharesDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double asked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double queue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double preparation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double model,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double runUnsplit,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Double posted) {}

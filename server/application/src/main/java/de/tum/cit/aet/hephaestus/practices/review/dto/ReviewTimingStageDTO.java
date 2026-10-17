package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewTimingStageDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Double medianSeconds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Double p90Seconds) {}

package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewStageTimingDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String stage,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant startedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant completedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer durationMs) {}

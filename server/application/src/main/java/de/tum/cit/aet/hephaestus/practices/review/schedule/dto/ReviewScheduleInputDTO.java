package de.tum.cit.aet.hephaestus.practices.review.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCadence;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewScheduleInputDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewCadence cadence,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer lookbackDays,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String budgetUsd) {}

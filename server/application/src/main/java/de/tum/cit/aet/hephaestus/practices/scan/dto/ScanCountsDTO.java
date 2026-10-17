package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer files,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer practiceItemPairs,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviewed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reused,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int met,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notMet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int doesNotApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int couldNotDetermine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int problems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int openProblems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheld,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notReviewed) {}

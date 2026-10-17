package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewPracticeOutcomesDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int met,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notMet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int doesNotApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int couldNotDetermine) {}

package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewFeedbackCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int awaitingApproval,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ready,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int delivered,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unconfirmed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheld,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int rejected,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int replaced,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed) {}

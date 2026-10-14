package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackCopyCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int planned,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int writeStarted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int posted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheld,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int retracted) {}

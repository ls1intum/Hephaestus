package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackResolutionCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long addressed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long disagree,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long notApplicable) {}

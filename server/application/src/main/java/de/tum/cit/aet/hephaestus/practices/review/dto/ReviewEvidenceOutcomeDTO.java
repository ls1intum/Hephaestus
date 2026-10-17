package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewEvidenceOutcomeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OutcomeVectorDTO outcomes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheld,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notReviewed) {}

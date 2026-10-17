package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewBucketDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant start,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int reviews,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int feedback) {}

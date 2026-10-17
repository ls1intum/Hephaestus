package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreateReviewRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        @NotNull
        @Pattern(regexp = "^[1-9][0-9]*$")
        String itemId) {}

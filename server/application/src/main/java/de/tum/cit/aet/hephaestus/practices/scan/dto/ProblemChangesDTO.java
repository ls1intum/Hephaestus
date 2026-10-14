package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProblemChangesDTO(
        @JsonProperty("new") int added,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unchanged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int updated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int absent) {}

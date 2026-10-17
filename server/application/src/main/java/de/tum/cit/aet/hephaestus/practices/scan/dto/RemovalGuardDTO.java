package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.RemovalGuardState;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RemovalGuardDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemovalGuardState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int previousFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int wouldRemove) {}

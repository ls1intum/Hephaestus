package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreateResultMarkRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultMarkKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}

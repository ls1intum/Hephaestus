package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProblemSummaryDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Severity severity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary) {}

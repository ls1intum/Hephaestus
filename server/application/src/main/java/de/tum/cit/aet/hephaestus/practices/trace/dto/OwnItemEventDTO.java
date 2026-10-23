package de.tum.cit.aet.hephaestus.practices.trace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OwnItemEventDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {}

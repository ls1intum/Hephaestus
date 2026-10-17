package de.tum.cit.aet.hephaestus.practices.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OverviewWindowDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant since,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant until) {}

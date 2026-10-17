package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanProgressDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer listedPairs,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int terminalPairs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int activeJobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int queuedJobs) {}

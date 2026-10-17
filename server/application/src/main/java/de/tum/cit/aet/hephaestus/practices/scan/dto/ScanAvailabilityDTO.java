package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanAvailabilityDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean expired,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant rawRetentionEndsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int withheldProofPairs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int expiredProofPairs) {}

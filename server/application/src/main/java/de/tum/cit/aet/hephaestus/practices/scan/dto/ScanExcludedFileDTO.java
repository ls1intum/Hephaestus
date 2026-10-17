package de.tum.cit.aet.hephaestus.practices.scan.dto;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemExclusionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record ScanExcludedFileDTO(
        @NonNull String itemId,
        @NonNull String path,
        @NonNull ItemExclusionReason reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes) {}

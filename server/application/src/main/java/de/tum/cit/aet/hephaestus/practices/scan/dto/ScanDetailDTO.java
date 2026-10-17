package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanDetailDTO(
        @JsonUnwrapped ScanSummaryDTO summary,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ScanPracticeRefDTO> practices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String budgetUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String chargedCostUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanProgressDTO progress) {
    public ScanDetailDTO {
        practices = List.copyOf(practices);
    }
}

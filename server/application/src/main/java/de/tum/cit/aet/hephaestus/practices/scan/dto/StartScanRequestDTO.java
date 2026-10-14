package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record StartScanRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String repositoryItemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String ref,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String expectedVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        List<String> practiceSlugs,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String budgetUsd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Boolean reuse) {
    public StartScanRequestDTO {
        practiceSlugs = practiceSlugs == null ? null : List.copyOf(practiceSlugs);
    }
}

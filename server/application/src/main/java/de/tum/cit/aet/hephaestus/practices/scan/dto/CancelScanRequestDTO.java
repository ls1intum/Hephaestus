package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CancelScanRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "CANCELLED")
        String status) {
    public CancelScanRequestDTO {
        if (!"CANCELLED".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}

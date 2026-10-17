package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CitationProofDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "VERIFIED")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemSha256,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String quoteSha256) {
    public CitationProofDTO {
        if (!"VERIFIED".equals(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
    }
}

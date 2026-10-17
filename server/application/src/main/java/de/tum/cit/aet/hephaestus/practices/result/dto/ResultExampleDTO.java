package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ResultExampleDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EvidenceCitationDTO citation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String note) {}

package de.tum.cit.aet.hephaestus.practices.groupdetail;

import io.swagger.v3.oas.annotations.media.Schema;

public record LegacyFeedbackItemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[1-9][0-9]*$")
        String itemId) {}

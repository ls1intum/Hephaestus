package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SetMemberHiddenRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hidden) {}

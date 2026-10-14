package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update team visibility settings in a workspace")
public record UpdateTeamSettingsRequestDTO(
        @NotNull(message = "hidden is required")
        @Schema(description = "Whether the team should be hidden from workspace activity")
        Boolean hidden) {}

package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for updating a workspace SCM Personal Access Token (PAT).
 */
@Schema(description = "Request to update the workspace's SCM Personal Access Token")
public record UpdateWorkspaceTokenRequestDTO(
    @NotBlank(message = "Personal access token is required")
    @Schema(description = "SCM Personal Access Token for API access")
    String personalAccessToken
) {}

package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for updating workspace Personal Access Token (PAT).
 */
public record UpdateWorkspaceTokenRequestDTO(
    @NotBlank(message = "Personal access token is required") String personalAccessToken
) {}

package de.tum.in.www1.hephaestus.workspace.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating workspace public visibility.
 */
public record UpdateWorkspacePublicVisibilityRequestDTO(
    @NotNull(message = "isPubliclyViewable flag is required") Boolean isPubliclyViewable
) {}

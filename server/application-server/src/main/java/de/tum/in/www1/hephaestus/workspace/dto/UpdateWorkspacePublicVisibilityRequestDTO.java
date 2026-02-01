package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating workspace public visibility.
 */
@Schema(description = "Request to update workspace public visibility setting")
public record UpdateWorkspacePublicVisibilityRequestDTO(
    @NotNull(message = "isPubliclyViewable flag is required")
    @Schema(
        description = "Whether the workspace should be publicly viewable without authentication",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Boolean isPubliclyViewable
) {}

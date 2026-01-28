package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating a workspace lifecycle status.
 */
@Schema(description = "Request to update the workspace lifecycle status")
public record UpdateWorkspaceStatusRequestDTO(
    @NotNull(message = "status is required")
    @Schema(
        description = "New lifecycle status (PENDING, ACTIVE, ARCHIVED)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    WorkspaceStatus status
) {}

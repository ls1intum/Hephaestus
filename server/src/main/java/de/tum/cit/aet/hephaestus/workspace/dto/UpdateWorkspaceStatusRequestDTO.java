package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.workspace.Workspace.WorkspaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating a workspace lifecycle status.
 */
@Schema(description = "Request to update the workspace lifecycle status")
public record UpdateWorkspaceStatusRequestDTO(
    @NotNull(message = "status is required")
    @Schema(
        description = "Target lifecycle status. PURGED is not a status transition: purge is irreversible and owner-only, so it lives at DELETE /workspaces/{workspaceSlug}.",
        allowableValues = { "ACTIVE", "SUSPENDED" }
    )
    WorkspaceStatus status
) {}

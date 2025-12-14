package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating a workspace lifecycle status.
 */
public record UpdateWorkspaceStatusRequestDTO(@NotNull(message = "status is required") WorkspaceStatus status) {}

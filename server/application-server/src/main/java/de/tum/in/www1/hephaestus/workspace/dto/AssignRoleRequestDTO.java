package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for assigning or updating a role for a workspace member.
 */
public record AssignRoleRequestDTO(@NotNull Long userId, @NotNull WorkspaceRole role) {}

package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for assigning or updating a role for a workspace member.
 */
@Schema(description = "Request to assign or update a user's role in a workspace")
public record AssignRoleRequestDTO(
    @NotNull
    @Schema(description = "User ID of the member to update", requiredMode = Schema.RequiredMode.REQUIRED)
    Long userId,
    @NotNull
    @Schema(description = "New role to assign (OWNER, ADMIN, MEMBER)", requiredMode = Schema.RequiredMode.REQUIRED)
    WorkspaceRole role
) {}

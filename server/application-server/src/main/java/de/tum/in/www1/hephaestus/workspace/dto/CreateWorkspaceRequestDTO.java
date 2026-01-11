package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for creating a new workspace.
 */
@Schema(description = "Request to create a new workspace")
public record CreateWorkspaceRequestDTO(
    @NotBlank(message = "Workspace slug is required")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{2,50}$",
        message = "Slug must be 3-51 characters, start with a lowercase letter or digit, and contain only lowercase letters, digits, or hyphens"
    )
    @Schema(
        description = "URL-friendly identifier for the workspace",
        example = "my-workspace",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String workspaceSlug,

    @NotBlank(message = "Display name is required")
    @Schema(
        description = "Human-readable name of the workspace",
        example = "My Workspace",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String displayName,

    @NotBlank(message = "Account login is required")
    @Schema(
        description = "GitHub account login to associate with this workspace",
        example = "my-org",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String accountLogin,

    @NotNull(message = "Account type is required")
    @Schema(description = "Type of GitHub account (USER or ORGANIZATION)", requiredMode = Schema.RequiredMode.REQUIRED)
    AccountType accountType,

    @NotNull(message = "Owner user ID is required")
    @Schema(description = "User ID of the workspace owner", requiredMode = Schema.RequiredMode.REQUIRED)
    Long ownerUserId
) {}

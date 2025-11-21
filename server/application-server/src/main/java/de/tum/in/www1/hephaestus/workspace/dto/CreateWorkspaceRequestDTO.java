package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for creating a new workspace.
 */
public record CreateWorkspaceRequestDTO(
    @NotBlank(message = "Workspace slug is required")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{2,50}$",
        message = "Slug must be 3-51 characters, start with a lowercase letter or digit, and contain only lowercase letters, digits, or hyphens"
    )
    String workspaceSlug,

    @NotBlank(message = "Display name is required") String displayName,

    @NotBlank(message = "Account login is required") String accountLogin,

    @NotNull(message = "Account type is required") AccountType accountType,

    @NotNull(message = "Owner user ID is required") Long ownerUserId
) {}

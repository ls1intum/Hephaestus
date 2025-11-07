package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for creating a new workspace.
 */
public record CreateWorkspaceRequestDTO(
    @NotBlank(message = "Slug is required")
    String slug,
    
    @NotBlank(message = "Display name is required")
    String displayName,
    
    @NotBlank(message = "Account login is required")
    String accountLogin,
    
    @NotNull(message = "Account type is required")
    AccountType accountType,
    
    @NotNull(message = "Owner user ID is required")
    Long ownerUserId
) {
}

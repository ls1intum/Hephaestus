package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to rename a workspace's URL slug")
public record RenameWorkspaceSlugRequestDTO(
    @NotBlank(message = "New slug is required")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{2,50}$",
        message = "Slug must be 3-51 characters, start with lowercase letter or digit, contain only lowercase letters, digits, and hyphens"
    )
    @Schema(
        description = "New URL-friendly identifier for the workspace",
        example = "new-workspace-slug",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String newSlug
) {}

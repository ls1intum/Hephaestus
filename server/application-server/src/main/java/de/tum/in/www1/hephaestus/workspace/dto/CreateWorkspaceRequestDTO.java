package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.core.security.ServerUrlValidator;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new workspace.
 *
 * <p>Supports both GitHub and GitLab workspaces via the optional {@code gitProviderMode} field.
 * When creating a GitLab workspace, {@code personalAccessToken} is required and
 * {@code serverUrl} may be provided for self-hosted instances.
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
    @Size(max = 120, message = "Display name must not exceed 120 characters")
    @Schema(
        description = "Human-readable name of the workspace",
        example = "My Workspace",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String displayName,

    @NotBlank(message = "Account login is required")
    @Size(max = 255, message = "Account login must not exceed 255 characters")
    @Schema(
        description = "Git provider account login (GitHub org/user or GitLab group path)",
        example = "my-org",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String accountLogin,

    @NotNull(message = "Account type is required")
    @Schema(description = "Type of account (USER or ORG)", requiredMode = Schema.RequiredMode.REQUIRED)
    AccountType accountType,

    @Deprecated(forRemoval = true)
    @Schema(
        description = "Deprecated: ignored by the server. The authenticated user always becomes the owner.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    Long ownerUserId,

    @Schema(
        description = "Git provider authentication mode. Defaults to PAT_ORG (GitHub PAT) if not specified.",
        example = "GITLAB_PAT"
    )
    Workspace.GitProviderMode gitProviderMode,

    @Size(max = 512, message = "Personal access token must not exceed 512 characters")
    @Schema(
        description = "Personal Access Token for GitLab API access. Required when gitProviderMode is GITLAB_PAT. Stored encrypted at rest.",
        example = "your-gitlab-token"
    )
    String personalAccessToken,

    @Schema(
        description = "Custom server URL for self-hosted GitLab instances. Must use HTTPS. Defaults to https://gitlab.com if not specified.",
        example = "https://gitlab.example.com"
    )
    String serverUrl
) {
    @AssertTrue(message = "Personal access token is required for GitLab PAT workspaces")
    @Schema(hidden = true)
    private boolean isTokenProvidedForGitLab() {
        if (gitProviderMode == Workspace.GitProviderMode.GITLAB_PAT) {
            return personalAccessToken != null && !personalAccessToken.isBlank();
        }
        return true;
    }

    @AssertTrue(message = "Server URL must use HTTPS and must not point to private/reserved addresses")
    @Schema(hidden = true)
    private boolean isServerUrlSafe() {
        if (serverUrl == null || serverUrl.isBlank()) {
            return true;
        }
        try {
            ServerUrlValidator.validate(serverUrl);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

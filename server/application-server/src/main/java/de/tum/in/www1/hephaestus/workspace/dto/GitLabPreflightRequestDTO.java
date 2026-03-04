package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for GitLab pre-creation checks (token validation and group listing).
 *
 * <p>The PAT is sent in the request body (not a header) because this is a POST
 * endpoint and the body is encrypted in transit via TLS.
 */
@Schema(description = "Request to validate a GitLab PAT or list accessible groups before workspace creation")
public record GitLabPreflightRequestDTO(
    @NotBlank(message = "Personal access token is required")
    @Schema(
        description = "GitLab Personal Access Token to validate",
        example = "glpat-xxxxxxxxxxxxxxxxxxxx",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String personalAccessToken,

    @Schema(
        description = "GitLab server URL. Defaults to https://gitlab.com if not specified.",
        example = "https://gitlab.example.com"
    )
    String serverUrl,

    @Schema(
        description = "GitLab group full path, used as fallback for group/project tokens that cannot access /api/v4/user",
        example = "my-org/my-team"
    )
    String groupFullPath
) {}

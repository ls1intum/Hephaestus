package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Available workspace creation providers and their configuration")
public record WorkspaceProvidersDTO(
    @Schema(
        description = "GitHub workspace provider config, null if not available",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    GitHubProviderDTO github,
    @Schema(
        description = "GitLab workspace provider config, null if not available",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    GitLabProviderDTO gitlab
) {
    // Avoid Spring's @Nullable on the parent fields above: springdoc 3.x propagates the
    // parent's nullability into the *referenced* schema's `type` (emits `type: "null"` for
    // GitHubProviderDTO etc.), which makes the generated TS client useless. Annotation-level
    // nullability via @Schema works correctly.
    @Schema(description = "GitHub provider configuration")
    public record GitHubProviderDTO(
        @Schema(
            description = "GitHub App installation URL",
            example = "https://github.com/apps/hephaestus/installations/new"
        ) String appInstallationUrl
    ) {}

    @Schema(description = "GitLab provider configuration")
    public record GitLabProviderDTO(
        @Schema(
            description = "Default GitLab server URL for this deployment",
            example = "https://gitlab.lrz.de"
        ) String defaultServerUrl
    ) {}
}

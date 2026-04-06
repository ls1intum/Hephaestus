package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Available workspace creation providers and their configuration")
public record WorkspaceProvidersDTO(
    @Nullable @Schema(description = "GitHub workspace provider config, null if not available") GitHubProviderDTO github,
    @Nullable @Schema(description = "GitLab workspace provider config, null if not available") GitLabProviderDTO gitlab
) {
    @Schema(description = "GitHub provider configuration")
    public record GitHubProviderDTO(
        @Schema(
            description = "GitHub App installation URL",
            example = "https://github.com/apps/hephaestus/installations/new"
        )
        String appInstallationUrl
    ) {}

    @Schema(description = "GitLab provider configuration")
    public record GitLabProviderDTO(
        @Schema(
            description = "Default GitLab server URL for this deployment",
            example = "https://gitlab.lrz.de"
        )
        String defaultServerUrl
    ) {}
}

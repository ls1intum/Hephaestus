package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Provides GitHub authentication credentials for a workspace.
 */
public interface InstallationTokenProvider {
    /** Get GitHub App installation ID for minting tokens. */
    Optional<Long> getInstallationId(Long workspaceId);

    /** Get personal access token (for PAT mode). */
    Optional<String> getPersonalAccessToken(Long workspaceId);

    /** Get authentication mode for a workspace. */
    AuthMode getAuthMode(Long workspaceId);

    enum AuthMode {
        GITHUB_APP_INSTALLATION,
        PERSONAL_ACCESS_TOKEN,
    }
}

package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Provides GitHub authentication credentials for a scope.
 */
public interface InstallationTokenProvider {
    /** Get GitHub App installation ID for minting tokens. */
    Optional<Long> getInstallationId(Long scopeId);

    /** Get personal access token (for PAT mode). */
    Optional<String> getPersonalAccessToken(Long scopeId);

    /** Get authentication mode for a scope. */
    AuthMode getAuthMode(Long scopeId);
}

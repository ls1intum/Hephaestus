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

    /**
     * Check if the scope is active and eligible for API operations.
     * Returns false for suspended or purged scopes to prevent wasted API calls.
     *
     * @param scopeId the scope to check
     * @return true if active, false if suspended/purged/not found
     */
    default boolean isScopeActive(Long scopeId) {
        return true; // Default: assume active for backward compatibility
    }
}

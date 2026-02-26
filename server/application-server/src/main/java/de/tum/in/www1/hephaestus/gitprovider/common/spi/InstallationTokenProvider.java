package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Provides authentication credentials for a scope (workspace).
 * <p>
 * This interface is provider-agnostic at the method level — both GitHub and GitLab
 * workspaces store personal access tokens, and both use scope IDs (workspace IDs)
 * for credential lookup. Provider-specific logic (GitHub App tokens, GitLab OAuth)
 * is handled by the respective token services that consume this SPI.
 */
public interface InstallationTokenProvider {
    /** Get GitHub App installation ID for minting tokens. */
    Optional<Long> getInstallationId(Long scopeId);

    /** Get personal access token (for PAT mode — works for both GitHub and GitLab PATs). */
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

    /**
     * Get the custom server URL for a scope (for self-hosted git provider instances).
     * <p>
     * Returns {@link Optional#empty()} when the scope uses the provider's default URL
     * (e.g., {@code https://gitlab.com} or {@code https://api.github.com}).
     *
     * @param scopeId the scope to check
     * @return the custom server URL, or empty if using provider default
     */
    default Optional<String> getServerUrl(Long scopeId) {
        return Optional.empty();
    }
}

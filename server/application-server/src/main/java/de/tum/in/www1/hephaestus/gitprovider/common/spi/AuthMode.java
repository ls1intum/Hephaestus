package de.tum.in.www1.hephaestus.gitprovider.common.spi;

/**
 * Authentication mode for GitHub API access.
 * <p>
 * This enum represents the two supported authentication mechanisms:
 * <ul>
 *   <li>{@link #GITHUB_APP} - Uses GitHub App installation tokens (recommended)</li>
 *   <li>{@link #PERSONAL_ACCESS_TOKEN} - Uses a Personal Access Token (PAT)</li>
 * </ul>
 * <p>
 * This is a shared enum used across the SPI layer to ensure consistent authentication
 * mode representation throughout the codebase.
 */
public enum AuthMode {
    /**
     * GitHub App installation authentication.
     * <p>
     * Uses short-lived installation tokens minted from the GitHub App.
     * This is the recommended authentication mode as it provides:
     * <ul>
     *   <li>Fine-grained permissions</li>
     *   <li>Automatic token rotation</li>
     *   <li>Higher rate limits</li>
     *   <li>Organization-level access</li>
     * </ul>
     */
    GITHUB_APP,

    /**
     * Personal Access Token authentication.
     * <p>
     * Uses a user-provided PAT stored in the workspace configuration.
     * This mode is useful for:
     * <ul>
     *   <li>Simple personal use cases</li>
     *   <li>Organizations that don't allow GitHub App installation</li>
     *   <li>Testing and development</li>
     * </ul>
     */
    PERSONAL_ACCESS_TOKEN,
}

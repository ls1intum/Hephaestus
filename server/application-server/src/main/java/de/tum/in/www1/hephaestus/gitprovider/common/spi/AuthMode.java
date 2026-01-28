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
     * Personal Access Token authentication for local development.
     * <p>
     * Uses a user-provided PAT stored in the workspace configuration.
     * This mode is intended for <b>local development</b> where setting up
     * a full GitHub App with webhooks is impractical.
     * <p>
     * Configure via:
     * <ul>
     *   <li>{@code hephaestus.workspace.init-default: true}</li>
     *   <li>{@code hephaestus.workspace.default.token: <your-PAT>}</li>
     * </ul>
     * <p>
     * <b>Not recommended for production.</b> Use {@link #GITHUB_APP} instead.
     *
     * @see de.tum.in.www1.hephaestus.workspace.WorkspaceProperties
     */
    PERSONAL_ACCESS_TOKEN,
}

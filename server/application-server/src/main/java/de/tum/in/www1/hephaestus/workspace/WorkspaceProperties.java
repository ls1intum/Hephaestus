package de.tum.in.www1.hephaestus.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for workspace initialization and management.
 *
 * <p>This record consolidates all workspace-related configuration under the
 * {@code hephaestus.workspace} prefix. It controls whether a default workspace
 * is automatically initialized at application startup.
 *
 * <h2>Local Development vs Production</h2>
 * <p>This configuration is primarily intended for <b>local development</b> environments
 * where setting up a full GitHub App with webhooks is impractical. In production,
 * workspaces are automatically created via GitHub App installations.
 *
 * <h3>Local Development (PAT Mode)</h3>
 * <pre>{@code
 * hephaestus:
 *   workspace:
 *     init-default: true          # Enable PAT workspace bootstrap
 *     default:
 *       login: my-github-org      # GitHub org/user to sync
 *       token: ghp_xxxxxxxxxxxx   # Personal Access Token
 *       repositories-to-monitor:
 *         - my-github-org/repo1
 *         - my-github-org/repo2
 * }</pre>
 *
 * <h3>Production (GitHub App Mode)</h3>
 * <pre>{@code
 * hephaestus:
 *   workspace:
 *     init-default: false         # Disable PAT bootstrap
 *   github:
 *     app:
 *       id: 12345                 # GitHub App ID
 *       private-key: ...          # GitHub App private key
 * }</pre>
 *
 * <p><strong>Cross-field validation:</strong> When {@code initDefault} is {@code true},
 * the {@code default.login} and {@code default.token} fields must be provided; otherwise,
 * configuration validation will fail at application startup.
 *
 * @param initDefault whether to initialize a default workspace at startup (default: {@code false})
 * @param defaultProperties configuration for the default workspace; required when {@code initDefault} is {@code true}
 * @see DefaultProperties
 * @see Workspace.GitProviderMode
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.workspace")
public record WorkspaceProperties(
    @DefaultValue("false") boolean initDefault,
    @Valid DefaultProperties defaultProperties
) {
    /**
     * Compact constructor that ensures nested properties are never null and performs
     * cross-field validation.
     *
     * @param initDefault whether to initialize the default workspace
     * @param defaultProperties configuration for the default workspace
     * @throws IllegalStateException if {@code initDefault} is {@code true} but required
     *                               credentials ({@code login} or {@code token}) are missing
     */
    public WorkspaceProperties {
        if (defaultProperties == null) {
            defaultProperties = new DefaultProperties(null, null, List.of());
        }
        // Cross-field validation: when initDefault=true, login and token must be present
        if (initDefault) {
            if (defaultProperties.login() == null || defaultProperties.login().isBlank()) {
                throw new IllegalStateException(
                    "hephaestus.workspace.default.login must not be blank when init-default is true"
                );
            }
            if (defaultProperties.token() == null || defaultProperties.token().isBlank()) {
                throw new IllegalStateException(
                    "hephaestus.workspace.default.token must not be blank when init-default is true"
                );
            }
        }
    }

    /**
     * Alias getter required so Spring Boot can bind configuration expressed under
     * the {@code default} key (since "default" is a reserved word in Java).
     *
     * @return the default workspace properties
     * @deprecated use {@link #defaultProperties()} instead; this method exists only
     *             for YAML binding compatibility
     */
    @Deprecated(forRemoval = false)
    @SuppressWarnings("DeprecatedIsStillUsed")
    public DefaultProperties getDefault() {
        return defaultProperties;
    }

    /**
     * Validates that credentials are provided when default workspace initialization is enabled.
     *
     * <p>This method is invoked by JSR-380 bean validation. The validation ensures that
     * when {@code initDefault} is {@code true}, both {@code login} and {@code token} in
     * {@link DefaultProperties} are non-blank.
     *
     * @return {@code true} if the configuration is valid; {@code false} otherwise
     */
    @AssertTrue(message = "When init-default is true, default.login and default.token must not be blank")
    private boolean isCredentialsValidWhenInitDefaultEnabled() {
        if (!initDefault) {
            return true;
        }
        return (
            defaultProperties != null &&
            defaultProperties.login() != null &&
            !defaultProperties.login().isBlank() &&
            defaultProperties.token() != null &&
            !defaultProperties.token().isBlank()
        );
    }

    /**
     * Configuration for the default workspace.
     *
     * <p>Contains the credentials and repository list used when initializing
     * a default workspace. The {@code login} and {@code token} are used to
     * authenticate with the Git provider (e.g., GitHub).
     *
     * @param login the username or organization login for the Git provider;
     *              required when workspace initialization is enabled
     * @param token the authentication token (e.g., GitHub PAT) for API access;
     *              required when workspace initialization is enabled
     * @param repositoriesToMonitor list of repository identifiers (in {@code owner/repo} format)
     *                              to monitor in the default workspace (default: empty list)
     */
    public record DefaultProperties(
        @Nullable String login,
        @Nullable String token,
        List<String> repositoriesToMonitor
    ) {
        /**
         * Compact constructor ensuring the repository list is never null.
         *
         * @param login the Git provider login/username
         * @param token the authentication token
         * @param repositoriesToMonitor the list of repositories to monitor
         */
        public DefaultProperties {
            if (repositoriesToMonitor == null) {
                repositoriesToMonitor = List.of();
            }
        }
    }
}

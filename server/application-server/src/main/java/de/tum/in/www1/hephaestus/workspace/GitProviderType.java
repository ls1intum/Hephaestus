package de.tum.in.www1.hephaestus.workspace;

/**
 * High-level git provider identity, derived from {@link Workspace.GitProviderMode}.
 *
 * <p>Used to distinguish provider-specific behavior (API clients, sync engines, UI icons)
 * without coupling to the specific authentication mechanism.
 */
public enum GitProviderType {
    GITHUB,
    GITLAB;

    /**
     * Derives the provider type from the authentication mode.
     *
     * @param mode the git provider mode (nullable — defaults to {@link #GITHUB} for backward compatibility)
     * @return the provider type
     */
    public static GitProviderType fromGitProviderMode(Workspace.GitProviderMode mode) {
        if (mode == null) {
            return GITHUB;
        }
        return switch (mode) {
            case PAT_ORG, GITHUB_APP_INSTALLATION -> GITHUB;
            case GITLAB_PAT -> GITLAB;
        };
    }
}

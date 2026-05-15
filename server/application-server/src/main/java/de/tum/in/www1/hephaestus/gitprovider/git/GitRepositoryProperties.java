package de.tum.in.www1.hephaestus.gitprovider.git;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for local git repository management.
 * <p>
 * Configure via application.yml:
 * <pre>
 * hephaestus:
 *   git:
 *     storage-path: /data/git-repos
 *     enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "hephaestus.git")
public record GitRepositoryProperties(
    /**
     * Base path where git repositories are stored.
     * Each repository is stored at {storagePath}/{repositoryId}
     * Defaults to /data/git-repos
     */
    String storagePath,

    /**
     * Whether local git checkout is enabled.
     * When disabled, commits are synced via API only (no file-level data).
     * Defaults to false.
     */
    boolean enabled
) {
    public GitRepositoryProperties {
        if (storagePath == null || storagePath.isBlank()) {
            storagePath = "/data/git-repos";
        }
    }
}

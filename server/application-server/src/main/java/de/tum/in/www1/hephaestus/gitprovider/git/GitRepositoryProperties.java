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
    boolean enabled,

    /**
     * Max age (days) of an unaccessed working-tree before {@code GitRepositoryReaper}
     * evicts it from disk. The agent re-clones on next demand — small one-time latency
     * cost in exchange for bounded disk usage. Defaults to 30 days.
     * <p>
     * Set to 0 to disable the reaper entirely (e.g. local development).
     */
    int cacheMaxAgeDays
) {
    public GitRepositoryProperties {
        if (storagePath == null || storagePath.isBlank()) {
            storagePath = "/data/git-repos";
        }
        if (cacheMaxAgeDays < 0) {
            cacheMaxAgeDays = 30;
        }
    }
}

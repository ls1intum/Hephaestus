package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.time.Duration;

/**
 * Constants used across GitHub synchronization services.
 * <p>
 * Centralizes common configuration values to avoid duplication and ensure
 * consistency across sync services.
 */
public final class GitHubSyncConstants {

    private GitHubSyncConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Default page size for GraphQL queries (100 items per page).
     * <p>
     * Used for most sync operations like issues, pull requests, and comments.
     * GitHub API allows up to 100 items per page, so we use the maximum to minimize API calls.
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * Large page size for GraphQL queries (100 items per page).
     * <p>
     * Used for bulk operations like labels, milestones, and other simpler entities.
     */
    public static final int LARGE_PAGE_SIZE = 100;

    /**
     * Default timeout for GraphQL operations.
     * <p>
     * Used when blocking on reactive GraphQL client responses.
     */
    public static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Extended timeout for complex GraphQL operations.
     * <p>
     * Used for operations that may take longer, such as syncing review comments
     * with nested thread structures.
     */
    public static final Duration EXTENDED_GRAPHQL_TIMEOUT = Duration.ofSeconds(60);
}

package de.tum.in.www1.hephaestus.gitprovider.common.github;

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

    // ========================================================================
    // API Configuration
    // ========================================================================

    /**
     * GitHub REST API base URL.
     */
    public static final String GITHUB_API_BASE_URL = "https://api.github.com";

    // ========================================================================
    // Pagination
    // ========================================================================

    /**
     * Default page size for GraphQL queries (100 items per page).
     * <p>
     * Used for most sync operations like issues and comments.
     * GitHub API allows up to 100 items per page, so we use the maximum to minimize API calls.
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * Page size for pull request sync GraphQL queries.
     * <p>
     * Uses a smaller page size (25) because the PR query has triple-nested pagination:
     * pullRequests -> reviewThreads -> comments, which creates exponential data volume.
     * With 100 PRs, the worst case is 100 x 10 threads x 10 comments = 10,000 review comments
     * per query, causing GitHub API timeouts (502 errors) on large repositories.
     * <p>
     * With 25 PRs per page: 25 x 10 x 10 = 2,500 review comments - much more manageable.
     */
    public static final int PR_SYNC_PAGE_SIZE = 25;

    /**
     * Large page size for GraphQL queries (100 items per page).
     * <p>
     * Used for bulk operations like labels, milestones, and other simpler entities.
     */
    public static final int LARGE_PAGE_SIZE = 100;

    /**
     * Maximum number of pages to fetch in pagination loops.
     * <p>
     * Acts as a defensive upper bound to prevent infinite loops if the API
     * incorrectly reports hasNextPage=true forever or returns the same cursor.
     * With 100 items per page, this allows up to 100,000 items before stopping.
     * If this limit is reached, a warning is logged and the loop exits gracefully.
     */
    public static final int MAX_PAGINATION_PAGES = 1000;
}

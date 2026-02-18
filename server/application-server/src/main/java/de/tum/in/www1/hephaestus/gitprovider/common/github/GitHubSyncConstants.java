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
     * Uses a small page size because the PR query has triple-nested pagination:
     * pullRequests -> reviews -> reviewThreads -> comments, which creates exponential
     * data volume. With the nested collection sizes of 5, each PR can generate up to
     * 5 reviews + 5 threads × 5 comments = 30 nodes. At 10 PRs per page, worst case
     * is ~300 nested nodes per query — well within GitHub's compute budget.
     * <p>
     * Previous value of 25 caused GitHub API timeouts (502 errors) on repositories
     * with heavy review activity (e.g., Artemis with 10,000+ PRs).
     */
    public static final int PR_SYNC_PAGE_SIZE = 10;

    /**
     * Large page size for GraphQL queries (100 items per page).
     * <p>
     * Used for bulk operations like labels, milestones, and other simpler entities.
     */
    public static final int LARGE_PAGE_SIZE = 100;

    // ========================================================================
    // Project Sync Page Sizes (Cost-Optimized)
    // ========================================================================
    //
    // GitHub GraphQL API cost formula: cost = ceil(total_nodes_requested / 100)
    // Nested collections MULTIPLY: items(50) * fieldValues(20) = 1,000 nodes = 10 cost points
    //
    // BEFORE optimization (worst case per page):
    //   items(100) * fieldValues(100) = 10,000 nodes
    //   + labels(50) = 500,000 additional nodes
    //   + users(50) + reviewers(50) + pullRequests(20) = millions of potential nodes
    //   Estimated cost: 5,000+ points per page (exceeds rate limits quickly)
    //
    // AFTER optimization (worst case per page):
    //   items(50) * fieldValues(20) = 1,000 nodes = 10 cost points
    //   + labels(10) * 1,000 = 10,000 nodes = 100 cost points
    //   + users(10) + reviewers(10) + pullRequests(5) = ~25,000 additional nodes
    //   Estimated cost: ~360 points per page (sustainable for large projects)
    //
    // Reference: https://docs.github.com/en/graphql/overview/rate-limits-and-node-limits

    /**
     * Page size for project items sync.
     * <p>
     * Uses 50 instead of 100 because project items have deeply nested field values.
     * Each item can have up to 20 field values (inline), and each field value may
     * contain nested collections (labels, users, reviewers, pull requests).
     * <p>
     * Cost calculation: 50 items * 20 fieldValues = 1,000 base nodes = 10 cost points
     * Plus nested collections, total ~360 cost points per page (sustainable).
     */
    public static final int PROJECT_ITEM_PAGE_SIZE = 50;

    /**
     * Inline page size for field values within project items.
     * <p>
     * Uses 20 instead of 100 to reduce multiplicative cost in nested queries.
     * Most project items have fewer than 20 custom fields. Items with more fields
     * will trigger follow-up pagination using GetProjectItemFieldValues query.
     * <p>
     * GitHub's recommendation: Use 8-20 for nested collections to reduce cost.
     * We use 20 as a balance between completeness and cost efficiency.
     */
    public static final int FIELD_VALUES_INLINE_SIZE = 20;

    /**
     * Page size for field value follow-up pagination.
     * <p>
     * When an item has more than 20 field values (the inline size), we fetch
     * the remaining field values using a separate query (GetProjectItemFieldValues).
     * Since this is a top-level query (not nested), we can use a larger page size.
     * <p>
     * Uses 50 to balance API efficiency with response size.
     */
    public static final int FIELD_VALUES_PAGINATION_SIZE = 50;

    /**
     * Page size for labels within field values (triple-nested).
     * <p>
     * Minimal size (10) because this is triply-nested: items -> fieldValues -> labels.
     * Most issues have fewer than 10 labels. The multiplicative cost impact is:
     * 50 items * 20 fieldValues * 10 labels = 10,000 potential nodes.
     */
    public static final int NESTED_LABELS_SIZE = 10;

    /**
     * Page size for users within field values (triple-nested).
     * <p>
     * Minimal size (10) because this is triply-nested: items -> fieldValues -> users.
     * Most field values reference fewer than 10 users (assignees, watchers, etc.).
     */
    public static final int NESTED_USERS_SIZE = 10;

    /**
     * Page size for reviewers within field values (triple-nested).
     * <p>
     * Minimal size (10) because this is triply-nested: items -> fieldValues -> reviewers.
     * Most PRs have fewer than 10 reviewers.
     */
    public static final int NESTED_REVIEWERS_SIZE = 10;

    /**
     * Page size for pull requests within field values (triple-nested).
     * <p>
     * Very small size (5) because this is triply-nested and PR data is heavy.
     * Field values linking to multiple PRs are rare; most reference 1-2 PRs.
     */
    public static final int NESTED_PULL_REQUESTS_SIZE = 5;

    /**
     * Page size for project status updates.
     * <p>
     * Uses the full 100 because status updates have minimal nesting (only creator).
     * Status updates are simple records with no deeply nested collections.
     * Cost: 100 status updates = 1 cost point (very efficient).
     */
    public static final int STATUS_UPDATE_PAGE_SIZE = 100;

    /**
     * Page size for project fields.
     * <p>
     * Uses 50 because field definitions include options for single-select fields.
     * Most projects have fewer than 50 custom fields. Options are not deeply nested.
     * Cost: 50 fields with ~10 options each = 500 nodes = 5 cost points.
     */
    public static final int PROJECT_FIELD_PAGE_SIZE = 50;

    /**
     * Maximum number of pages to fetch in pagination loops.
     * <p>
     * Acts as a defensive upper bound to prevent infinite loops if the API
     * incorrectly reports hasNextPage=true forever or returns the same cursor.
     * With 100 items per page, this allows up to 100,000 items before stopping.
     * If this limit is reached, a warning is logged and the loop exits gracefully.
     */
    public static final int MAX_PAGINATION_PAGES = 1000;

    // Transport retry configuration
    public static final int TRANSPORT_MAX_RETRIES = 3;
    public static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    public static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);
    public static final double JITTER_FACTOR = 0.5;

    // ========================================================================
    // Adaptive Page Sizing
    // ========================================================================

    /**
     * Remaining-points threshold below which page sizes are halved.
     */
    private static final int LOW_REMAINING_THRESHOLD = 500;

    /**
     * Remaining-points threshold below which page sizes are quartered.
     */
    private static final int CRITICAL_REMAINING_THRESHOLD = 100;

    /**
     * Returns an adjusted page size based on current rate-limit budget.
     * <p>
     * When the rate-limit budget is healthy the base page size is returned
     * unchanged. As the budget drops, the page size is reduced to slow down
     * point consumption and give the budget time to reset:
     * <ul>
     *   <li>{@code remaining >= 500} → full {@code basePageSize}</li>
     *   <li>{@code 100 <= remaining < 500} → {@code basePageSize / 2} (min 10)</li>
     *   <li>{@code remaining < 100} → {@code basePageSize / 4} (min 5)</li>
     * </ul>
     *
     * @param basePageSize the nominal page size (e.g. {@link #DEFAULT_PAGE_SIZE})
     * @param remaining    current rate-limit points remaining for the scope
     * @return the (possibly reduced) page size to use for the next query
     */
    public static int adaptPageSize(int basePageSize, int remaining) {
        if (remaining >= LOW_REMAINING_THRESHOLD) {
            return basePageSize;
        }
        if (remaining >= CRITICAL_REMAINING_THRESHOLD) {
            return Math.max(10, basePageSize / 2);
        }
        return Math.max(5, basePageSize / 4);
    }
}

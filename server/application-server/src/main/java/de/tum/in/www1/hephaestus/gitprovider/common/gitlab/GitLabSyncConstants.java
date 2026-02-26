package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constants used across GitLab synchronization services.
 * <p>
 * Centralizes common configuration values to avoid duplication and ensure
 * consistency across sync services. Mirrors the structure of
 * {@link de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants}
 * but with GitLab-specific values.
 *
 * <h2>Key Differences from GitHub</h2>
 * <ul>
 *   <li>Rate limits: 100 points/minute for GraphQL (vs GitHub's 5000/hour)</li>
 *   <li>IDs: {@code gid://gitlab/<Type>/<id>} format requiring numeric extraction</li>
 *   <li>Rate limit headers: {@code RateLimit-Remaining} (not {@code X-RateLimit-*})</li>
 * </ul>
 */
public final class GitLabSyncConstants {

    private GitLabSyncConstants() {
        // Utility class - prevent instantiation
    }

    // ========================================================================
    // API Configuration
    // ========================================================================

    /** Default GitLab server URL (gitlab.com SaaS). */
    public static final String GITLAB_DEFAULT_SERVER_URL = "https://gitlab.com";

    /** Path to the GitLab GraphQL API endpoint. */
    public static final String GITLAB_GRAPHQL_PATH = "/api/graphql";

    /** Path to the GitLab REST API v4 endpoint. */
    public static final String GITLAB_REST_API_PATH = "/api/v4";

    // ========================================================================
    // Rate Limit Headers
    // ========================================================================

    /** Header containing the remaining rate limit points. */
    public static final String HEADER_RATE_LIMIT_REMAINING = "RateLimit-Remaining";

    /** Header containing the total rate limit. */
    public static final String HEADER_RATE_LIMIT_LIMIT = "RateLimit-Limit";

    /** Header containing the rate limit reset time (Unix epoch seconds). */
    public static final String HEADER_RATE_LIMIT_RESET = "RateLimit-Reset";

    /** Header containing the points consumed by the current request. */
    public static final String HEADER_RATE_LIMIT_OBSERVED = "RateLimit-Observed";

    // ========================================================================
    // Rate Limit Thresholds
    // ========================================================================

    /**
     * Default rate limit for GitLab GraphQL API (100 points per minute).
     * <p>
     * GitLab's GraphQL rate limit is significantly lower than GitHub's (5000/hour).
     * Each query has a calculated complexity cost deducted from this budget.
     *
     * @see <a href="https://docs.gitlab.com/ee/api/graphql/index.html#limits">GitLab GraphQL Limits</a>
     */
    public static final int DEFAULT_RATE_LIMIT = 100;

    /**
     * Low threshold below which we consider throttling (15% of default).
     * <p>
     * With only 100 points/minute, we must be conservative — 15 points
     * gives roughly 3-5 more queries before exhaustion.
     */
    public static final int LOW_REMAINING_THRESHOLD = 15;

    /**
     * Critical threshold below which we pause completely (5% of default).
     * <p>
     * At 5 points remaining, we stop making requests and wait for reset.
     */
    public static final int CRITICAL_REMAINING_THRESHOLD = 5;

    /**
     * GitLab rate limit window duration (60 seconds).
     */
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);

    // ========================================================================
    // Pagination
    // ========================================================================

    /**
     * Default page size for GitLab GraphQL queries (100 items per page).
     * <p>
     * GitLab API allows up to 100 items per page for most endpoints.
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * Page size for issue sync GraphQL queries.
     * <p>
     * Reduced from default because GitLab issue queries embed notes,
     * labels, assignees, and milestone data per issue.
     */
    public static final int ISSUE_PAGE_SIZE = 20;

    /**
     * Page size for merge request sync GraphQL queries.
     * <p>
     * Reduced because MR queries have nested reviews, discussions,
     * and approval data — similar to GitHub PR sync constraints.
     */
    public static final int MERGE_REQUEST_PAGE_SIZE = 10;

    /**
     * Large page size for simple entity queries (labels, milestones).
     */
    public static final int LARGE_PAGE_SIZE = 100;

    /**
     * Maximum number of pages to fetch in pagination loops.
     * <p>
     * Defensive upper bound to prevent infinite loops.
     */
    public static final int MAX_PAGINATION_PAGES = 1000;

    // ========================================================================
    // Transport Retry Configuration
    // ========================================================================

    /** Maximum retries for transport-level errors. */
    public static final int TRANSPORT_MAX_RETRIES = 3;

    /** Initial backoff delay for transport retries. */
    public static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);

    /** Maximum backoff delay for transport retries. */
    public static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);

    /** Jitter factor for retry backoff (0.0 to 1.0). */
    public static final double JITTER_FACTOR = 0.5;

    // ========================================================================
    // GitLab Global ID Parsing
    // ========================================================================

    /**
     * Pattern for GitLab Global IDs: {@code gid://gitlab/<Type>/<numericId>}.
     * <p>
     * GitLab uses globally unique IDs in GraphQL responses. The numeric ID
     * at the end corresponds to the database ID used in REST API responses.
     *
     * @see <a href="https://docs.gitlab.com/ee/api/graphql/index.html#gitlab-custom-scalars">GitLab Custom Scalars</a>
     */
    private static final Pattern GID_PATTERN = Pattern.compile("^gid://gitlab/[A-Za-z]+/(\\d+)$");

    /**
     * Extracts the numeric ID from a GitLab Global ID string.
     * <p>
     * Example: {@code "gid://gitlab/Project/123"} → {@code 123L}
     *
     * @param globalId the GitLab Global ID (e.g., {@code "gid://gitlab/User/42"})
     * @return the numeric database ID
     * @throws IllegalArgumentException if the format is invalid
     */
    public static long extractNumericId(String globalId) {
        if (globalId == null || globalId.isBlank()) {
            throw new IllegalArgumentException("GitLab Global ID must not be null or blank");
        }

        Matcher matcher = GID_PATTERN.matcher(globalId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid GitLab Global ID format: '" + globalId + "'. Expected: gid://gitlab/<Type>/<numericId>"
            );
        }

        return Long.parseLong(matcher.group(1));
    }

    // ========================================================================
    // Adaptive Page Sizing
    // ========================================================================

    /**
     * Returns an adjusted page size based on current rate-limit budget.
     * <p>
     * When the rate-limit budget is healthy the base page size is returned
     * unchanged. As the budget drops, the page size is reduced to slow down
     * point consumption and give the budget time to reset:
     * <ul>
     *   <li>{@code remaining >= 15} → full {@code basePageSize}</li>
     *   <li>{@code 5 <= remaining < 15} → {@code basePageSize / 2} (min 10)</li>
     *   <li>{@code remaining < 5} → {@code basePageSize / 4} (min 5)</li>
     * </ul>
     *
     * @param basePageSize the nominal page size (e.g., {@link #DEFAULT_PAGE_SIZE})
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

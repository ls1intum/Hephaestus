package de.tum.in.www1.hephaestus.gitprovider.common.github;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects silent data truncation in GitHub GraphQL connection responses.
 * <p>
 * GitHub GraphQL connections return a fixed page of items (determined by the {@code first:} argument)
 * plus a {@code totalCount} field. When {@code totalCount} exceeds the number of fetched nodes, it
 * indicates that more data exists than was returned. If the caller does not follow up with pagination,
 * data is silently lost.
 * <p>
 * This utility provides a single place to log a warning whenever such overflow is detected,
 * making it easy to spot data loss in production logs and add follow-up pagination where needed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // For connections where totalCount is available:
 * GraphQlConnectionOverflowDetector.check(
 *     "assignees", nodes.size(), connection.getTotalCount(), "PR #123 in owner/repo"
 * );
 *
 * // For connections where only hasNextPage is available:
 * GraphQlConnectionOverflowDetector.check(
 *     "labels", nodes.size(), hasNextPage, "field value for item XYZ"
 * );
 * }</pre>
 *
 * @see GraphQlPaginationHelper for the complementary pagination loop helper
 */
@Slf4j
public final class GraphQlConnectionOverflowDetector {

    private GraphQlConnectionOverflowDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks whether a GraphQL connection returned fewer nodes than {@code totalCount} indicates.
     * <p>
     * Logs a warning when overflow is detected (i.e., {@code totalCount > fetchedCount}).
     * This is the primary detection method when the GraphQL query includes {@code totalCount}.
     *
     * @param connectionName  human-readable name of the connection (e.g. "assignees", "reviews")
     * @param fetchedCount    number of nodes actually fetched ({@code nodes.size()})
     * @param totalCount      total count reported by the GraphQL connection
     * @param context         contextual description for the log message (e.g. "PR #42 in owner/repo")
     * @return {@code true} if overflow was detected (data may be incomplete)
     */
    public static boolean check(String connectionName, int fetchedCount, int totalCount, String context) {
        if (totalCount > fetchedCount) {
            log.warn(
                "GraphQL connection overflow: connection={}, fetchedCount={}, totalCount={}, context={}. " +
                    "Data may be incomplete — consider adding follow-up pagination.",
                connectionName,
                fetchedCount,
                totalCount,
                context
            );
            return true;
        }
        return false;
    }

    /**
     * Checks whether a GraphQL connection has more pages that were not fetched.
     * <p>
     * Logs a warning when {@code hasNextPage} is {@code true}, indicating that the connection
     * returned only a partial result. This variant is used for nested connections where
     * {@code totalCount} is not available but {@code pageInfo.hasNextPage} is.
     *
     * @param connectionName  human-readable name of the connection (e.g. "labels", "users")
     * @param fetchedCount    number of nodes actually fetched ({@code nodes.size()})
     * @param hasNextPage     whether the connection reports more pages
     * @param context         contextual description for the log message
     * @return {@code true} if overflow was detected (data may be incomplete)
     */
    public static boolean check(String connectionName, int fetchedCount, boolean hasNextPage, String context) {
        if (hasNextPage) {
            log.warn(
                "GraphQL connection overflow: connection={}, fetchedCount={}, hasNextPage=true, context={}. " +
                    "Data may be incomplete — consider adding follow-up pagination.",
                connectionName,
                fetchedCount,
                context
            );
            return true;
        }
        return false;
    }
}

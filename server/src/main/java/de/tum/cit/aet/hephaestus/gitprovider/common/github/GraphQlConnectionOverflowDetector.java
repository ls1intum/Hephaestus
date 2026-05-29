package de.tum.cit.aet.hephaestus.gitprovider.common.github;

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
 * <h2>When to use</h2>
 * Use this detector for connections where overflow means <b>actual data loss</b> — i.e., the caller
 * has no follow-up pagination and the truncated items will never be fetched.
 *
 * <h2>When NOT to use</h2>
 * <ul>
 *   <li><b>Embedded DTOs with pagination</b>: If the DTO already tracks {@code hasNextPage}/{@code endCursor}
 *       and the caller follows up with additional API calls (e.g., {@code EmbeddedReviewsDTO},
 *       {@code EmbeddedCommentsDTO}), the overflow is handled — don't warn about it.</li>
 *   <li><b>Incremental sync</b>: When syncing only recently-updated items, {@code fetchedCount < totalCount}
 *       is expected by design — the caller intentionally skips unchanged items.</li>
 * </ul>
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
     * Checks a <b>single-page</b> connection that has no follow-up pagination (e.g. an embedded
     * {@code assignees}/{@code labels} connection on a DTO). Here {@code totalCount > fetchedCount}
     * genuinely means the extra items will never be fetched, so it is logged at WARN.
     *
     * <p><b>Do not</b> use this overload from inside a pagination loop: a count gap that remains
     * after the loop exhausted every page is a benign unit/over-report discrepancy, not data loss.
     * Use {@link #checkPaginated} there and pass whether the loop stopped early — it demotes the
     * benign case to DEBUG and only WARNs on real truncation.
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
                "GraphQL embedded connection truncated: connection={}, fetchedCount={}, totalCount={}, context={}. " +
                    "No follow-up pagination — remaining items were not fetched.",
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
     * Checks completeness of a <b>paginated</b> connection, gating severity on <em>why the loop
     * ended</em> rather than the count comparison alone.
     *
     * <p>If the loop ran to completion ({@code stoppedEarly == false}), any residual
     * {@code totalCount > fetchedCount} gap is benign — a unit/over-report discrepancy, not loss —
     * and is logged at DEBUG. Only an early stop (error, rate-limit, or page cap leaving pages
     * unfetched) is real incompleteness and logged at WARN.
     *
     * @param connectionName human-readable connection name (e.g. "reviewThreads", "issues")
     * @param fetchedCount   nodes received across all pages — must be apples-to-apples with
     *                       {@code totalCount} (count what the connection counts, not post-filter results)
     * @param totalCount     total reported by the connection
     * @param stoppedEarly   {@code true} if the loop broke before exhausting all pages
     * @param context        contextual description for the log message
     * @return {@code true} if data is incomplete (gap present and the loop stopped early)
     */
    public static boolean checkPaginated(
        String connectionName,
        int fetchedCount,
        int totalCount,
        boolean stoppedEarly,
        String context
    ) {
        if (totalCount <= fetchedCount) {
            return false;
        }
        if (stoppedEarly) {
            log.warn(
                "GraphQL connection truncated by early stop: connection={}, fetchedCount={}, totalCount={}, " +
                    "context={}. Pagination stopped before all pages were retrieved; data is incomplete.",
                connectionName,
                fetchedCount,
                totalCount,
                context
            );
            return true;
        }
        log.debug(
            "GraphQL connection count gap after full pagination (benign): connection={}, fetchedCount={}, " +
                "totalCount={}, context={}.",
            connectionName,
            fetchedCount,
            totalCount,
            context
        );
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
                "GraphQL embedded connection truncated: connection={}, fetchedCount={}, hasNextPage=true, " +
                    "context={}. No follow-up pagination — remaining items were not fetched.",
                connectionName,
                fetchedCount,
                context
            );
            return true;
        }
        return false;
    }
}

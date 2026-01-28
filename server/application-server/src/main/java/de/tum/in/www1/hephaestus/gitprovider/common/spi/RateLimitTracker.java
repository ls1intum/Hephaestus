package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import java.time.Duration;
import java.time.Instant;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;

/**
 * SPI for tracking GitHub API rate limits per scope/installation.
 *
 * <p>GitHub rate limits are per-installation (GitHub App) or per-token (PAT),
 * not global. This interface provides scope-aware rate limit tracking to
 * ensure accurate throttling decisions in multi-workspace deployments.
 *
 * <p>Implementations must be thread-safe as multiple sync threads may
 * access the same scope's rate limit state concurrently.
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.common.github.ScopedRateLimitTracker
 */
public interface RateLimitTracker {

    /**
     * Updates rate limit state from a GraphQL response for a specific scope.
     *
     * @param scopeId the scope that made the API call
     * @param response the GraphQL response containing rate limit data
     * @return the extracted rate limit info, or null if not present
     */
    @Nullable
    GHRateLimit updateFromResponse(Long scopeId, @Nullable ClientGraphQlResponse response);

    /**
     * Gets the remaining rate limit points for a scope.
     *
     * <p>Returns a default value (5000) if the scope has never been updated,
     * which is safe for first-sync scenarios.
     *
     * @param scopeId the scope to check
     * @return remaining points, or default if unknown
     */
    int getRemaining(Long scopeId);

    /**
     * Gets the total rate limit for a scope.
     *
     * @param scopeId the scope to check
     * @return total limit points per hour
     */
    int getLimit(Long scopeId);

    /**
     * Checks if the rate limit is critically low for a scope.
     *
     * <p>Critical means we should stop making requests immediately
     * to avoid exhausting the limit entirely.
     *
     * @param scopeId the scope to check
     * @return true if remaining points are below critical threshold
     */
    boolean isCritical(Long scopeId);

    /**
     * Checks if the rate limit is below the low threshold for a scope.
     *
     * <p>Low means we should consider throttling or skipping non-essential
     * operations like backfill.
     *
     * @param scopeId the scope to check
     * @return true if remaining points are below low threshold
     */
    boolean isLow(Long scopeId);

    /**
     * Waits if the rate limit is critical, using the scope's reset time.
     *
     * <p>This blocks the calling thread until either:
     * <ul>
     *   <li>The rate limit resets</li>
     *   <li>The maximum wait duration (5 minutes) is reached</li>
     * </ul>
     *
     * @param scopeId the scope to check
     * @return true if waited, false if no wait was needed
     * @throws InterruptedException if interrupted while waiting
     */
    boolean waitIfNeeded(Long scopeId) throws InterruptedException;

    /**
     * Gets the reset time for a scope's rate limit.
     *
     * @param scopeId the scope to check
     * @return reset time, or null if unknown
     */
    @Nullable
    Instant getResetAt(Long scopeId);

    /**
     * Gets recommended delay based on rate limit state for a scope.
     *
     * <p>When rate limit is low, this provides adaptive throttling that
     * distributes remaining points evenly until reset.
     *
     * @param scopeId the scope to check
     * @return recommended delay, or Duration.ZERO if no throttling needed
     */
    Duration getRecommendedDelay(Long scopeId);
}

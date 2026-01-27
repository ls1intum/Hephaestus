package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tracks GitHub GraphQL API rate limit information from query responses.
 * <p>
 * GitHub's GraphQL API uses a point-based rate limiting system where each query
 * costs a certain number of points based on complexity. This differs from the
 * REST API's simple request count limit. The rate limit information is returned
 * in every GraphQL response when requested.
 * <p>
 * This tracker:
 * <ul>
 * <li>Extracts rate limit data from GraphQL responses</li>
 * <li>Exposes metrics via Micrometer for monitoring and alerting</li>
 * <li>Provides proactive throttling to prevent hitting the limit</li>
 * <li>Logs warnings when approaching the rate limit threshold</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * ClientGraphQlResponse response = client.documentName("GetPullRequests")
 *     .variable("owner", "example")
 *     .execute()
 *     .block();
 *
 * rateLimitTracker.updateFromResponse(response);
 * rateLimitTracker.waitIfNeeded(); // Blocks if rate limit is low
 * }</pre>
 *
 * @see <a href="https://docs.github.com/en/graphql/overview/rate-limits-and-node-limits-for-the-graphql-api">
 *      GitHub GraphQL Rate Limits</a>
 */
@Component
public class GitHubGraphQlRateLimitTracker {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQlRateLimitTracker.class);

    /**
     * Default threshold below which we consider the rate limit "low".
     * When remaining points drop below this, we start proactive throttling.
     * GitHub App installations get 5000 points/hour, so 500 is 10%.
     */
    private static final int DEFAULT_LOW_THRESHOLD = 500;

    /**
     * Critical threshold below which we pause completely until reset.
     * At this point we're dangerously close to hitting the limit.
     */
    private static final int CRITICAL_THRESHOLD = 100;

    /**
     * Minimum time to wait between checks when throttling (prevents busy-waiting).
     */
    private static final Duration MIN_WAIT_DURATION = Duration.ofSeconds(1);

    /**
     * Maximum time to wait in a single blocking call.
     */
    private static final Duration MAX_WAIT_DURATION = Duration.ofMinutes(5);

    // Rate limit state - thread-safe for concurrent access from multiple sync services
    private final AtomicInteger remaining = new AtomicInteger(5000);
    private final AtomicInteger limit = new AtomicInteger(5000);
    private final AtomicInteger used = new AtomicInteger(0);
    private final AtomicInteger lastQueryCost = new AtomicInteger(0);
    private final AtomicReference<Instant> resetAt = new AtomicReference<>(Instant.now().plusSeconds(3600));

    // Configurable threshold
    private final AtomicInteger lowThreshold = new AtomicInteger(DEFAULT_LOW_THRESHOLD);

    public GitHubGraphQlRateLimitTracker(MeterRegistry meterRegistry) {
        // Register metrics for monitoring dashboards and alerting
        Gauge.builder("github.graphql.ratelimit.points.remaining", remaining, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit points remaining")
            .tag("api", "graphql")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.points.limit", limit, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit total points per hour")
            .tag("api", "graphql")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.points.used", used, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit points used in current window")
            .tag("api", "graphql")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.last_query_cost", lastQueryCost, AtomicInteger::get)
            .description("Cost of the last GitHub GraphQL query")
            .tag("api", "graphql")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.seconds_until_reset", this, tracker -> {
            Instant reset = tracker.resetAt.get();
            if (reset == null) {
                return 0.0;
            }
            long seconds = Duration.between(Instant.now(), reset).getSeconds();
            return Math.max(0, seconds);
        })
            .description("Seconds until GitHub GraphQL rate limit resets")
            .tag("api", "graphql")
            .register(meterRegistry);
    }

    /**
     * Extracts and updates rate limit information from a GraphQL response.
     * <p>
     * Call this method after every GraphQL query execution to keep the
     * rate limit tracking up to date.
     *
     * @param response the GraphQL response containing rate limit data
     * @return the extracted rate limit info, or null if not present
     */
    @Nullable
    public GHRateLimit updateFromResponse(@Nullable ClientGraphQlResponse response) {
        if (response == null || !response.isValid()) {
            return null;
        }

        try {
            GHRateLimit rateLimit = response.field("rateLimit").toEntity(GHRateLimit.class);
            if (rateLimit != null) {
                updateFromRateLimit(rateLimit);
                return rateLimit;
            }
        } catch (Exception e) {
            // Rate limit field may not be present in all queries
            log.trace("Could not extract rate limit from response: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Updates the tracker state from a rate limit object.
     *
     * @param rateLimit the rate limit information from a GraphQL response
     */
    public void updateFromRateLimit(GHRateLimit rateLimit) {
        if (rateLimit == null) {
            return;
        }

        int newRemaining = rateLimit.getRemaining();
        int newLimit = rateLimit.getLimit();
        int newUsed = rateLimit.getUsed();
        int cost = rateLimit.getCost();

        // Update atomic values
        remaining.set(newRemaining);
        limit.set(newLimit);
        used.set(newUsed);
        lastQueryCost.set(cost);

        // Update reset time
        OffsetDateTime resetTime = rateLimit.getResetAt();
        if (resetTime != null) {
            resetAt.set(resetTime.toInstant());
        }

        // Calculate actual used points (limit - remaining is more reliable than getUsed())
        int actualUsed = newLimit > 0 ? newLimit - newRemaining : 0;

        // Log based on severity
        if (newRemaining < CRITICAL_THRESHOLD) {
            log.error(
                "CRITICAL: GitHub GraphQL rate limit nearly exhausted: remaining={}, limit={}, used={}, cost={}, resetAt={}",
                newRemaining,
                newLimit,
                actualUsed,
                cost,
                resetTime
            );
        } else if (newRemaining < lowThreshold.get()) {
            log.warn(
                "GitHub GraphQL rate limit low: remaining={}, limit={}, used={}, cost={}, resetAt={}",
                newRemaining,
                newLimit,
                actualUsed,
                cost,
                resetTime
            );
        }
        // Normal operations: only log at TRACE to avoid spam
    }

    /**
     * Checks if the current rate limit is below the low threshold.
     *
     * @return true if remaining points are below the threshold
     */
    public boolean isLow() {
        return remaining.get() < lowThreshold.get();
    }

    /**
     * Checks if the current rate limit is critically low.
     *
     * @return true if remaining points are below the critical threshold
     */
    public boolean isCritical() {
        return remaining.get() < CRITICAL_THRESHOLD;
    }

    /**
     * Waits if the rate limit is critically low.
     * <p>
     * This method blocks the calling thread if remaining points are below
     * the critical threshold, waiting until either:
     * <ul>
     * <li>The rate limit resets (based on resetAt time)</li>
     * <li>The maximum wait duration is reached</li>
     * </ul>
     * <p>
     * If the rate limit is low but not critical, this method returns immediately
     * but logs a warning.
     *
     * @return true if the method waited, false if no waiting was needed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean waitIfNeeded() throws InterruptedException {
        int currentRemaining = remaining.get();

        if (currentRemaining >= lowThreshold.get()) {
            return false; // No waiting needed
        }

        if (currentRemaining >= CRITICAL_THRESHOLD) {
            // Low but not critical - just log and continue
            log.info("Rate limit low but continuing: remaining={}, threshold={}", currentRemaining, lowThreshold.get());
            return false;
        }

        // Critical - need to wait
        Instant reset = resetAt.get();
        if (reset == null) {
            log.warn("Rate limit critical but no reset time available, waiting minimum duration");
            Thread.sleep(MIN_WAIT_DURATION.toMillis());
            return true;
        }

        Duration waitTime = Duration.between(Instant.now(), reset);

        // Clamp to reasonable bounds
        if (waitTime.isNegative() || waitTime.isZero()) {
            // Reset time has passed, the limit should have reset
            log.info("Rate limit reset time has passed, continuing");
            return false;
        }

        if (waitTime.compareTo(MAX_WAIT_DURATION) > 0) {
            waitTime = MAX_WAIT_DURATION;
        }

        log.warn(
            "Pausing due to critical rate limit: remaining={}, waitSeconds={}, resetAt={}",
            currentRemaining,
            waitTime.getSeconds(),
            reset
        );

        Thread.sleep(waitTime.toMillis());
        return true;
    }

    /**
     * Calculates recommended delay between requests based on current rate limit state.
     * <p>
     * This provides adaptive throttling that slows down as we approach the limit,
     * distributing remaining points evenly across time until reset.
     *
     * @return recommended delay between requests, or Duration.ZERO if no throttling needed
     */
    public Duration getRecommendedDelay() {
        int currentRemaining = remaining.get();

        if (currentRemaining >= lowThreshold.get()) {
            return Duration.ZERO;
        }

        Instant reset = resetAt.get();
        if (reset == null || reset.isBefore(Instant.now())) {
            return Duration.ZERO;
        }

        // Calculate time until reset
        Duration timeUntilReset = Duration.between(Instant.now(), reset);

        if (currentRemaining <= 0) {
            // No points left, wait for reset
            return timeUntilReset.compareTo(MAX_WAIT_DURATION) > 0 ? MAX_WAIT_DURATION : timeUntilReset;
        }

        // Distribute remaining points evenly across remaining time
        // Add a buffer to avoid hitting exactly zero
        int effectiveRemaining = Math.max(1, currentRemaining - CRITICAL_THRESHOLD);
        long delayMillis = timeUntilReset.toMillis() / effectiveRemaining;

        // Clamp to reasonable bounds
        delayMillis = Math.max(MIN_WAIT_DURATION.toMillis(), delayMillis);
        delayMillis = Math.min(MAX_WAIT_DURATION.toMillis(), delayMillis);

        return Duration.ofMillis(delayMillis);
    }

    /**
     * Sets the low threshold for rate limit warnings and throttling.
     *
     * @param threshold the new threshold value
     */
    public void setLowThreshold(int threshold) {
        lowThreshold.set(threshold);
    }

    /**
     * Gets the current remaining points.
     *
     * @return remaining rate limit points
     */
    public int getRemaining() {
        return remaining.get();
    }

    /**
     * Gets the total rate limit.
     *
     * @return total rate limit points per hour
     */
    public int getLimit() {
        return limit.get();
    }

    /**
     * Gets the points used in the current window.
     *
     * @return used rate limit points
     */
    public int getUsed() {
        return used.get();
    }

    /**
     * Gets the cost of the last query.
     *
     * @return cost of the last GraphQL query
     */
    public int getLastQueryCost() {
        return lastQueryCost.get();
    }

    /**
     * Gets the time when the rate limit resets.
     *
     * @return reset time, or null if unknown
     */
    @Nullable
    public Instant getResetAt() {
        return resetAt.get();
    }
}

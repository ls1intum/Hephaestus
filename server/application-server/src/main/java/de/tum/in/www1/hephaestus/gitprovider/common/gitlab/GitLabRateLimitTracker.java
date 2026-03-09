package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.CRITICAL_REMAINING_THRESHOLD;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.DEFAULT_RATE_LIMIT;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_LIMIT;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_OBSERVED;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_REMAINING;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_RESET;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.LOW_REMAINING_THRESHOLD;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-scope rate limit tracker for GitLab API with Micrometer metrics.
 *
 * <p>GitLab rate limits are per-token (PAT) and use a 60-second sliding window
 * with 100 points per minute for GraphQL API. This is fundamentally different
 * from GitHub's 5000 points per hour budget.
 *
 * <p>Unlike GitHub (which exposes rate limit data in GraphQL response fields),
 * GitLab exposes rate limits exclusively through HTTP response headers:
 * <ul>
 *   <li>{@code RateLimit-Remaining} — points remaining in current window</li>
 *   <li>{@code RateLimit-Limit} — total points allowed per window</li>
 *   <li>{@code RateLimit-Reset} — Unix timestamp when window resets</li>
 *   <li>{@code RateLimit-Observed} — points consumed by the current request</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Uses {@link ConcurrentHashMap} for scope state
 * and atomic variables for individual state fields.
 *
 * <h2>Memory Management</h2>
 * <p>State entries are automatically evicted after 24 hours of inactivity.
 *
 * @see <a href="https://docs.gitlab.com/ee/api/graphql/index.html#limits">GitLab GraphQL Limits</a>
 * @see de.tum.in.www1.hephaestus.gitprovider.common.github.ScopedRateLimitTracker
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("System-wide rate limit tracking — tracks GitLab API limits per-scope across all workspaces")
public class GitLabRateLimitTracker {

    /**
     * Maximum time to wait when rate limit is critical.
     * GitLab resets every 60 seconds, so 70s gives a comfortable margin.
     */
    private static final Duration MAX_WAIT_DURATION = Duration.ofSeconds(70);

    /** Minimum wait to avoid busy-waiting. */
    private static final Duration MIN_WAIT_DURATION = Duration.ofSeconds(1);

    /** Default reset window (60 seconds from now) for newly created state. */
    private static final int DEFAULT_RESET_SECONDS = 60;

    /** Time after which inactive scope state is evicted (24 hours). */
    private static final Duration EVICTION_THRESHOLD = Duration.ofHours(24);

    /** Metric name prefix for rate limit gauges. */
    private static final String METRIC_PREFIX = "gitlab.graphql.ratelimit";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Long, ScopeRateLimitState> stateByScope = new ConcurrentHashMap<>();

    public GitLabRateLimitTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Updates rate limit state from HTTP response headers for a specific scope.
     *
     * @param scopeId the scope (workspace ID) that made the API call
     * @param headers the HTTP response headers containing rate limit data
     */
    public void updateFromHeaders(Long scopeId, @Nullable HttpHeaders headers) {
        if (scopeId == null || headers == null) {
            return;
        }

        String remainingStr = headers.getFirst(HEADER_RATE_LIMIT_REMAINING);
        String limitStr = headers.getFirst(HEADER_RATE_LIMIT_LIMIT);
        String resetStr = headers.getFirst(HEADER_RATE_LIMIT_RESET);
        String observedStr = headers.getFirst(HEADER_RATE_LIMIT_OBSERVED);

        if (remainingStr == null && limitStr == null) {
            return; // No rate limit headers present
        }

        try {
            ScopeRateLimitState state = getOrCreateState(scopeId);

            if (remainingStr != null) {
                state.remaining.set(Integer.parseInt(remainingStr));
            }
            if (limitStr != null) {
                state.limit.set(Integer.parseInt(limitStr));
            }
            if (resetStr != null) {
                state.resetAt.set(Instant.ofEpochSecond(Long.parseLong(resetStr)));
            }
            if (observedStr != null) {
                state.lastQueryCost.set(Integer.parseInt(observedStr));
            }

            // Calculate used points
            int currentLimit = state.limit.get();
            int currentRemaining = state.remaining.get();
            state.used.set(currentLimit - currentRemaining);
            state.lastUpdated.set(Instant.now());

            // Log based on severity
            if (currentRemaining < CRITICAL_REMAINING_THRESHOLD) {
                log.error(
                    "CRITICAL: GitLab GraphQL rate limit nearly exhausted: scopeId={}, remaining={}, limit={}, resetAt={}",
                    scopeId,
                    currentRemaining,
                    currentLimit,
                    state.resetAt.get()
                );
            } else if (currentRemaining < LOW_REMAINING_THRESHOLD) {
                log.warn(
                    "GitLab GraphQL rate limit low: scopeId={}, remaining={}, limit={}, resetAt={}",
                    scopeId,
                    currentRemaining,
                    currentLimit,
                    state.resetAt.get()
                );
            }
        } catch (NumberFormatException e) {
            log.debug("Could not parse GitLab rate limit headers for scope {}: {}", scopeId, e.getMessage());
        }
    }

    /**
     * Gets the remaining rate limit points for a scope.
     * Returns the default limit if the scope has never been updated.
     */
    public int getRemaining(Long scopeId) {
        if (scopeId == null) {
            return DEFAULT_RATE_LIMIT;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        if (state == null) {
            return DEFAULT_RATE_LIMIT;
        }
        int remaining = state.remaining.get();
        // Optimistic reset: if remaining is low and reset time has passed
        if (remaining < LOW_REMAINING_THRESHOLD) {
            Instant resetAt = state.resetAt.get();
            if (resetAt != null && !Instant.now().isBefore(resetAt)) {
                int fullLimit = state.limit.get();
                state.remaining.set(fullLimit);
                log.info(
                    "GitLab rate limit reset time passed, optimistically reset: scopeId={}, oldRemaining={}, newRemaining={}",
                    scopeId,
                    remaining,
                    fullLimit
                );
                return fullLimit;
            }
        }
        return remaining;
    }

    /** Gets the total rate limit for a scope. */
    public int getLimit(Long scopeId) {
        if (scopeId == null) {
            return DEFAULT_RATE_LIMIT;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        return state != null ? state.limit.get() : DEFAULT_RATE_LIMIT;
    }

    /** Checks if the rate limit is critically low. */
    public boolean isCritical(Long scopeId) {
        return getRemaining(scopeId) < CRITICAL_REMAINING_THRESHOLD;
    }

    /** Checks if the rate limit is below the low threshold. */
    public boolean isLow(Long scopeId) {
        return getRemaining(scopeId) < LOW_REMAINING_THRESHOLD;
    }

    /**
     * Waits if the rate limit is critical, blocking until reset.
     *
     * @return true if waited, false if no wait was needed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitIfNeeded(Long scopeId) throws InterruptedException {
        int currentRemaining = getRemaining(scopeId);

        if (currentRemaining >= LOW_REMAINING_THRESHOLD) {
            return false;
        }

        if (currentRemaining >= CRITICAL_REMAINING_THRESHOLD) {
            log.info(
                "GitLab rate limit low but continuing: scopeId={}, remaining={}, threshold={}",
                scopeId,
                currentRemaining,
                LOW_REMAINING_THRESHOLD
            );
            return false;
        }

        // Critical — need to wait
        Instant reset = getResetAt(scopeId);
        if (reset == null) {
            log.warn("GitLab rate limit critical but no reset time, waiting minimum: scopeId={}", scopeId);
            Thread.sleep(MIN_WAIT_DURATION.toMillis());
            return true;
        }

        Duration waitTime = Duration.between(Instant.now(), reset);

        if (waitTime.isNegative() || waitTime.isZero()) {
            // Reset time has passed — optimistically reset
            ScopeRateLimitState state = stateByScope.get(scopeId);
            if (state != null) {
                int fullLimit = state.limit.get();
                state.remaining.set(fullLimit);
                log.info("GitLab rate limit reset time passed, optimistically reset: scopeId={}", scopeId);
            }
            return false;
        }

        if (waitTime.compareTo(MAX_WAIT_DURATION) > 0) {
            waitTime = MAX_WAIT_DURATION;
        }

        log.warn(
            "Pausing due to critical GitLab rate limit: scopeId={}, remaining={}, waitSeconds={}, resetAt={}",
            scopeId,
            currentRemaining,
            waitTime.getSeconds(),
            reset
        );

        Thread.sleep(waitTime.toMillis());
        return true;
    }

    /** Gets the reset time for a scope's rate limit. */
    @Nullable
    public Instant getResetAt(Long scopeId) {
        if (scopeId == null) {
            return null;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        return state != null ? state.resetAt.get() : null;
    }

    /**
     * Gets recommended delay based on rate limit state.
     *
     * @return recommended delay, or Duration.ZERO if no throttling needed
     */
    public Duration getRecommendedDelay(Long scopeId) {
        int currentRemaining = getRemaining(scopeId);

        if (currentRemaining >= LOW_REMAINING_THRESHOLD) {
            return Duration.ZERO;
        }

        Instant reset = getResetAt(scopeId);
        if (reset == null || reset.isBefore(Instant.now())) {
            return Duration.ZERO;
        }

        Duration timeUntilReset = Duration.between(Instant.now(), reset);

        if (currentRemaining <= 0) {
            return timeUntilReset.compareTo(MAX_WAIT_DURATION) > 0 ? MAX_WAIT_DURATION : timeUntilReset;
        }

        // Distribute remaining points evenly across remaining time
        int effectiveRemaining = Math.max(1, currentRemaining - CRITICAL_REMAINING_THRESHOLD);
        long delayMillis = timeUntilReset.toMillis() / effectiveRemaining;

        delayMillis = Math.max(MIN_WAIT_DURATION.toMillis(), delayMillis);
        delayMillis = Math.min(MAX_WAIT_DURATION.toMillis(), delayMillis);

        return Duration.ofMillis(delayMillis);
    }

    /** Gets the number of tracked scopes (for monitoring/debugging). */
    public int getTrackedScopeCount() {
        return stateByScope.size();
    }

    /**
     * Evicts stale scope state to prevent unbounded memory growth.
     * Runs every hour, removes entries inactive for 24+ hours.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void evictStaleEntries() {
        Instant threshold = Instant.now().minus(EVICTION_THRESHOLD);
        List<Long> scopesToEvict = new ArrayList<>();

        for (Map.Entry<Long, ScopeRateLimitState> entry : stateByScope.entrySet()) {
            Instant lastUpdated = entry.getValue().lastUpdated.get();
            if (lastUpdated != null && lastUpdated.isBefore(threshold)) {
                scopesToEvict.add(entry.getKey());
            }
        }

        if (scopesToEvict.isEmpty()) {
            log.debug("GitLab rate limit eviction: no stale entries, tracked scopes={}", stateByScope.size());
            return;
        }

        for (Long scopeId : scopesToEvict) {
            stateByScope.remove(scopeId);
            deregisterMetrics(scopeId);
            log.info("Evicted stale GitLab rate limit state: scopeId={}", scopeId);
        }

        log.info(
            "GitLab rate limit eviction completed: evicted={}, remaining={}",
            scopesToEvict.size(),
            stateByScope.size()
        );
    }

    private void deregisterMetrics(Long scopeId) {
        String scopeTag = String.valueOf(scopeId);
        List<Meter.Id> toRemove = new ArrayList<>();

        meterRegistry
            .getMeters()
            .forEach(meter -> {
                String meterName = meter.getId().getName();
                if (meterName.startsWith(METRIC_PREFIX)) {
                    String tagValue = meter.getId().getTag("scope_id");
                    if (scopeTag.equals(tagValue)) {
                        toRemove.add(meter.getId());
                    }
                }
            });

        for (Meter.Id meterId : toRemove) {
            meterRegistry.remove(meterId);
        }
    }

    private ScopeRateLimitState getOrCreateState(Long scopeId) {
        return stateByScope.computeIfAbsent(scopeId, id -> {
            ScopeRateLimitState state = new ScopeRateLimitState();
            registerMetrics(id, state);
            log.debug("Created GitLab rate limit state for scope: scopeId={}", id);
            return state;
        });
    }

    private void registerMetrics(Long scopeId, ScopeRateLimitState state) {
        Tags tags = Tags.of("scope_id", String.valueOf(scopeId));

        Gauge.builder(METRIC_PREFIX + ".points.remaining", state.remaining, AtomicInteger::get)
            .tags(tags)
            .description("GitLab GraphQL API rate limit points remaining")
            .register(meterRegistry);

        Gauge.builder(METRIC_PREFIX + ".points.limit", state.limit, AtomicInteger::get)
            .tags(tags)
            .description("GitLab GraphQL API rate limit total points per minute")
            .register(meterRegistry);

        Gauge.builder(METRIC_PREFIX + ".points.used", state.used, AtomicInteger::get)
            .tags(tags)
            .description("GitLab GraphQL API rate limit points used in current window")
            .register(meterRegistry);

        Gauge.builder(METRIC_PREFIX + ".last_query_cost", state.lastQueryCost, AtomicInteger::get)
            .tags(tags)
            .description("Points consumed by the last GitLab GraphQL query")
            .register(meterRegistry);

        Gauge.builder(METRIC_PREFIX + ".seconds_until_reset", state, s -> {
            Instant reset = s.resetAt.get();
            if (reset == null) {
                return 0.0;
            }
            long seconds = Duration.between(Instant.now(), reset).getSeconds();
            return Math.max(0, seconds);
        })
            .tags(tags)
            .description("Seconds until GitLab GraphQL rate limit resets")
            .register(meterRegistry);
    }

    /**
     * Encapsulates rate limit state for a single scope.
     * All fields are atomic for thread-safe concurrent access.
     */
    private static class ScopeRateLimitState {

        final AtomicInteger remaining = new AtomicInteger(DEFAULT_RATE_LIMIT);
        final AtomicInteger limit = new AtomicInteger(DEFAULT_RATE_LIMIT);
        final AtomicInteger used = new AtomicInteger(0);
        final AtomicInteger lastQueryCost = new AtomicInteger(0);
        final AtomicReference<Instant> resetAt = new AtomicReference<>(
            Instant.now().plusSeconds(DEFAULT_RESET_SECONDS)
        );
        final AtomicReference<Instant> lastUpdated = new AtomicReference<>(Instant.now());
    }
}

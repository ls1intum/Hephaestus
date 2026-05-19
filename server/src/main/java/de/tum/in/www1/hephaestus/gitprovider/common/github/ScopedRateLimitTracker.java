package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-scope rate limit tracker with Micrometer metrics.
 *
 * <p>GitHub rate limits are per-installation (GitHub App) or per-token (PAT).
 * This tracker maintains separate state for each scope/workspace, ensuring
 * accurate throttling decisions in multi-tenant deployments.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Uses {@link ConcurrentHashMap} for scope state
 * and atomic variables for individual state fields. Multiple sync threads can
 * safely access and update the same scope's rate limit concurrently.
 *
 * <h2>Memory Management</h2>
 * <p>State entries are created on first access and automatically evicted after
 * 24 hours of inactivity. This prevents unbounded memory growth in multi-tenant
 * deployments with many short-lived workspaces. Eviction runs every hour.
 *
 * <h2>Metrics</h2>
 * <p>Micrometer gauges are registered per-scope with a {@code scope_id} tag,
 * enabling per-workspace monitoring in Prometheus/Grafana dashboards.
 *
 * @see RateLimitTracker
 */
@Component
@Slf4j
@WorkspaceAgnostic("System-wide rate limit tracking - tracks GitHub API limits per-scope across all workspaces")
public class ScopedRateLimitTracker implements RateLimitTracker {

    /**
     * Default rate limit for GitHub App installations (5000 points/hour).
     * Used when a scope has never been updated.
     */
    private static final int DEFAULT_LIMIT = 5000;

    /**
     * Low threshold below which we consider throttling (10% of default).
     */
    private static final int DEFAULT_LOW_THRESHOLD = 500;

    /**
     * Critical threshold below which we pause completely (2% of default).
     */
    private static final int CRITICAL_THRESHOLD = 100;

    /**
     * Maximum time to wait in a single blocking call.
     */
    private static final Duration MAX_WAIT_DURATION = Duration.ofMinutes(5);

    /**
     * Minimum wait to avoid busy-waiting.
     */
    private static final Duration MIN_WAIT_DURATION = Duration.ofSeconds(1);

    /**
     * Time after which inactive scope state is evicted (24 hours).
     */
    private static final Duration EVICTION_THRESHOLD = Duration.ofHours(24);

    /**
     * Metric name prefix for rate limit gauges.
     */
    private static final String METRIC_PREFIX = "github.graphql.ratelimit";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Long, ScopeRateLimitState> stateByScope = new ConcurrentHashMap<>();

    public ScopedRateLimitTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public GHRateLimit updateFromResponse(Long scopeId, @Nullable ClientGraphQlResponse response) {
        if (scopeId == null || response == null || !response.isValid()) {
            return null;
        }

        try {
            GHRateLimit rateLimit = response.field("rateLimit").toEntity(GHRateLimit.class);
            if (rateLimit != null) {
                ScopeRateLimitState state = getOrCreateState(scopeId);
                state.update(rateLimit, scopeId);
                return rateLimit;
            }
        } catch (Exception e) {
            // Rate limit field may not be present in all queries
            log.trace("Could not extract rate limit from response for scope {}: {}", scopeId, e.getMessage());
        }
        return null;
    }

    @Override
    public int getRemaining(Long scopeId) {
        if (scopeId == null) {
            return DEFAULT_LIMIT;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        if (state == null) {
            return DEFAULT_LIMIT;
        }
        int remaining = state.remaining.get();
        // Optimistic reset: if remaining is low and reset time has passed,
        // reset remaining to full limit so callers don't stall on stale data.
        if (remaining < DEFAULT_LOW_THRESHOLD) {
            Instant resetAt = state.resetAt.get();
            if (resetAt != null && !Instant.now().isBefore(resetAt)) {
                int fullLimit = state.limit.get();
                state.remaining.set(fullLimit);
                log.info(
                    "Rate limit reset time has passed, optimistically reset remaining: scopeId={}, oldRemaining={}, newRemaining={}, resetAt={}",
                    scopeId,
                    remaining,
                    fullLimit,
                    resetAt
                );
                return fullLimit;
            }
        }
        return remaining;
    }

    @Override
    public int getLimit(Long scopeId) {
        if (scopeId == null) {
            return DEFAULT_LIMIT;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        return state != null ? state.limit.get() : DEFAULT_LIMIT;
    }

    @Override
    public boolean isCritical(Long scopeId) {
        return getRemaining(scopeId) < CRITICAL_THRESHOLD;
    }

    @Override
    public boolean isLow(Long scopeId) {
        return getRemaining(scopeId) < DEFAULT_LOW_THRESHOLD;
    }

    @Override
    public boolean waitIfNeeded(Long scopeId) throws InterruptedException {
        int currentRemaining = getRemaining(scopeId);

        if (currentRemaining >= DEFAULT_LOW_THRESHOLD) {
            return false; // No waiting needed
        }

        if (currentRemaining >= CRITICAL_THRESHOLD) {
            // Low but not critical - log and continue
            log.info(
                "Rate limit low but continuing: scopeId={}, remaining={}, threshold={}",
                scopeId,
                currentRemaining,
                DEFAULT_LOW_THRESHOLD
            );
            return false;
        }

        // Critical - need to wait
        Instant reset = getResetAt(scopeId);
        if (reset == null) {
            log.warn("Rate limit critical but no reset time available, waiting minimum duration: scopeId={}", scopeId);
            Thread.sleep(MIN_WAIT_DURATION.toMillis());
            return true;
        }

        Duration waitTime = Duration.between(Instant.now(), reset);

        // Clamp to reasonable bounds
        if (waitTime.isNegative() || waitTime.isZero()) {
            // Reset time has passed — optimistically reset remaining to full limit.
            // The next actual GraphQL call will update with the real remaining value.
            ScopeRateLimitState state = stateByScope.get(scopeId);
            if (state != null) {
                int fullLimit = state.limit.get();
                state.remaining.set(fullLimit);
                log.info(
                    "Rate limit reset time has passed, optimistically reset remaining: scopeId={}, remaining={}, resetAt={}",
                    scopeId,
                    fullLimit,
                    reset
                );
            } else {
                log.info("Rate limit reset time has passed, continuing: scopeId={}", scopeId);
            }
            return false;
        }

        if (waitTime.compareTo(MAX_WAIT_DURATION) > 0) {
            waitTime = MAX_WAIT_DURATION;
        }

        log.warn(
            "Pausing due to critical rate limit: scopeId={}, remaining={}, waitSeconds={}, resetAt={}",
            scopeId,
            currentRemaining,
            waitTime.getSeconds(),
            reset
        );

        Thread.sleep(waitTime.toMillis());
        return true;
    }

    @Override
    @Nullable
    public Instant getResetAt(Long scopeId) {
        if (scopeId == null) {
            return null;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        return state != null ? state.resetAt.get() : null;
    }

    @Override
    public Duration getRecommendedDelay(Long scopeId) {
        int currentRemaining = getRemaining(scopeId);

        if (currentRemaining >= DEFAULT_LOW_THRESHOLD) {
            return Duration.ZERO;
        }

        Instant reset = getResetAt(scopeId);
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
     * Gets the number of tracked scopes (for monitoring/debugging).
     *
     * @return number of scopes with rate limit state
     */
    public int getTrackedScopeCount() {
        return stateByScope.size();
    }

    /**
     * Scheduled eviction of stale scope state to prevent unbounded memory growth.
     * <p>
     * Runs every hour and removes entries that haven't been updated in 24 hours.
     * Also deregisters the associated Micrometer metrics to prevent gauge leaks.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void evictStaleEntries() {
        Instant threshold = Instant.now().minus(EVICTION_THRESHOLD);
        List<Long> scopesToEvict = new ArrayList<>();

        // Identify stale entries
        for (Map.Entry<Long, ScopeRateLimitState> entry : stateByScope.entrySet()) {
            Instant lastUpdated = entry.getValue().lastUpdated.get();
            if (lastUpdated != null && lastUpdated.isBefore(threshold)) {
                scopesToEvict.add(entry.getKey());
            }
        }

        if (scopesToEvict.isEmpty()) {
            log.debug("Rate limit eviction: no stale entries found, tracked scopes={}", stateByScope.size());
            return;
        }

        // Evict stale entries and their metrics
        for (Long scopeId : scopesToEvict) {
            stateByScope.remove(scopeId);
            deregisterMetrics(scopeId);
            log.info("Evicted stale rate limit state: scopeId={}", scopeId);
        }

        log.info("Rate limit eviction completed: evicted={}, remaining={}", scopesToEvict.size(), stateByScope.size());
    }

    /**
     * Deregisters all Micrometer metrics for a scope.
     */
    private void deregisterMetrics(Long scopeId) {
        String scopeTag = String.valueOf(scopeId);
        List<Meter.Id> toRemove = new ArrayList<>();

        // Find all meters with this scope_id tag
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

        // Remove the meters
        for (Meter.Id meterId : toRemove) {
            meterRegistry.remove(meterId);
        }

        log.debug("Deregistered {} metrics for scopeId={}", toRemove.size(), scopeId);
    }

    private ScopeRateLimitState getOrCreateState(Long scopeId) {
        return stateByScope.computeIfAbsent(scopeId, id -> {
            ScopeRateLimitState state = new ScopeRateLimitState();
            registerMetrics(id, state);
            log.debug("Created rate limit state for scope: scopeId={}", id);
            return state;
        });
    }

    private void registerMetrics(Long scopeId, ScopeRateLimitState state) {
        Tags tags = Tags.of("scope_id", String.valueOf(scopeId));

        Gauge.builder("github.graphql.ratelimit.points.remaining", state.remaining, AtomicInteger::get)
            .tags(tags)
            .description("GitHub GraphQL API rate limit points remaining")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.points.limit", state.limit, AtomicInteger::get)
            .tags(tags)
            .description("GitHub GraphQL API rate limit total points per hour")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.points.used", state.used, AtomicInteger::get)
            .tags(tags)
            .description("GitHub GraphQL API rate limit points used in current window")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.last_query_cost", state.lastQueryCost, AtomicInteger::get)
            .tags(tags)
            .description("Cost of the last GitHub GraphQL query")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.seconds_until_reset", state, s -> {
            Instant reset = s.resetAt.get();
            if (reset == null) {
                return 0.0;
            }
            long seconds = Duration.between(Instant.now(), reset).getSeconds();
            return Math.max(0, seconds);
        })
            .tags(tags)
            .description("Seconds until GitHub GraphQL rate limit resets")
            .register(meterRegistry);
    }

    /**
     * Encapsulates rate limit state for a single scope.
     * All fields are atomic for thread-safe concurrent access.
     */
    private static class ScopeRateLimitState {

        final AtomicInteger remaining = new AtomicInteger(DEFAULT_LIMIT);
        final AtomicInteger limit = new AtomicInteger(DEFAULT_LIMIT);
        final AtomicInteger used = new AtomicInteger(0);
        final AtomicInteger lastQueryCost = new AtomicInteger(0);
        final AtomicReference<Instant> resetAt = new AtomicReference<>(Instant.now().plusSeconds(3600));
        final AtomicReference<Instant> lastUpdated = new AtomicReference<>(Instant.now());

        void update(GHRateLimit rateLimit, Long scopeId) {
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
            lastUpdated.set(Instant.now());

            // Calculate actual used points (limit - remaining is more reliable)
            int actualUsed = newLimit > 0 ? newLimit - newRemaining : 0;

            // Log based on severity
            if (newRemaining < CRITICAL_THRESHOLD) {
                log.error(
                    "CRITICAL: GitHub GraphQL rate limit nearly exhausted: scopeId={}, remaining={}, limit={}, used={}, cost={}, resetAt={}",
                    scopeId,
                    newRemaining,
                    newLimit,
                    actualUsed,
                    cost,
                    resetTime
                );
            } else if (newRemaining < DEFAULT_LOW_THRESHOLD) {
                log.warn(
                    "GitHub GraphQL rate limit low: scopeId={}, remaining={}, limit={}, used={}, cost={}, resetAt={}",
                    scopeId,
                    newRemaining,
                    newLimit,
                    actualUsed,
                    cost,
                    resetTime
                );
            }
        }
    }
}

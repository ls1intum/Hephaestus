package de.tum.cit.aet.hephaestus.integration.scm.github.common;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.RateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHRateLimit;
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
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.client.ClientGraphQlResponse;
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
public class ScopedRateLimitTracker implements RateLimitTracker, RateLimitObservationSink {

    /**
     * Conservative assumed ceiling for an <em>unobserved</em> scope, used only by the throttle-decision
     * APIs ({@link #getRemaining}, {@link #getLimit}, {@link #waitIfNeeded}, {@link #getRecommendedDelay}).
     *
     * <p>It is a guess, not a fact: GitHub's real GraphQL ceiling is 5,000 points/hour for a plain
     * installation but scales to 12,500 for installations with more than 20 repositories and is 10,000 on
     * Enterprise Cloud. It therefore must never reach {@link #snapshot} — see
     * {@link RateLimitSnapshot} for the honesty rule. The seeded observation from REST
     * {@code GET /rate_limit} (see {@link #updateFromRestRateLimit}) is what makes the true ceiling known
     * before the first sync.
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
        return effectiveRemaining(state);
    }

    @Override
    public int getLimit(Long scopeId) {
        if (scopeId == null) {
            return DEFAULT_LIMIT;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        return state == null ? DEFAULT_LIMIT : limitOrDefault(state);
    }

    /**
     * The remaining budget to <em>throttle against</em> — a pure computation, never a write.
     *
     * <p>This is where the "optimistic reset" lives now. When the observed window has already closed, the
     * measured {@code remaining} says nothing about the current window, so decisions assume a full budget
     * rather than stalling on stale data. Previously this assumption was written back into the observed
     * state and then rendered to admins as a measured value; keeping it a return value makes that
     * impossible — {@link ScopeRateLimitState#update} is the only writer of observed fields.
     */
    private static int effectiveRemaining(ScopeRateLimitState state) {
        Integer observed = state.remaining.get();
        if (observed == null) {
            return limitOrDefault(state);
        }
        if (observed < DEFAULT_LOW_THRESHOLD) {
            Instant resetAt = state.resetAt.get();
            if (resetAt != null && !Instant.now().isBefore(resetAt)) {
                return limitOrDefault(state);
            }
        }
        return observed;
    }

    /** The observed ceiling, or the conservative assumption when GitHub has not reported one yet. */
    private static int limitOrDefault(ScopeRateLimitState state) {
        Integer limit = state.limit.get();
        return limit == null ? DEFAULT_LIMIT : limit;
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
            // The observed window has closed, so there is nothing to wait for. Note we do NOT write a
            // full budget back into the observed state — getRemaining()'s effectiveRemaining() already
            // treats a closed window as "assume full" for decisions, and the next GraphQL response
            // supplies the real number. Writing it back is what used to leak an invented value onto the
            // admin surface.
            log.info("Rate limit reset time has passed, continuing: scopeId={}, resetAt={}", scopeId, reset);
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
     * Seeds a real observation from REST {@code GET /rate_limit}'s {@code resources.graphql} entry.
     *
     * <p>This exists so the <em>true</em> ceiling is known before the first GraphQL call of a process.
     * GitHub's GraphQL ceiling is not a universal 5,000/hour — App installations with more than 20
     * repositories earn another 50 points/hour per repository up to 12,500, and Enterprise Cloud
     * installations get 10,000 — so an assumed 5,000 is wrong for exactly the large installations where
     * the number matters. The endpoint is documented as <b>not counting against the rate limit</b>
     * ("Accessing this endpoint does not count against your REST API rate limit"), which is why seeding
     * this way is honest rather than expensive: the values fed here were measured, not guessed.
     *
     * <p>Applied only if it is <em>newer</em> than whatever is already recorded, so a seed that loses a
     * race against a live GraphQL response cannot overwrite fresher data with staler data.
     *
     * @param scopeId    the scope the token belongs to
     * @param limit      {@code resources.graphql.limit}
     * @param remaining  {@code resources.graphql.remaining}
     * @param resetAt    {@code resources.graphql.reset}, or {@code null} if absent
     * @param observedAt when the REST response was received
     */
    @Override
    public void updateFromRestRateLimit(
        Long scopeId,
        int limit,
        int remaining,
        @Nullable Instant resetAt,
        Instant observedAt
    ) {
        if (scopeId == null) {
            return;
        }
        ScopeRateLimitState state = getOrCreateState(scopeId);
        Instant lastUpdated = state.lastUpdated.get();
        if (lastUpdated != null && !observedAt.isAfter(lastUpdated)) {
            return; // a fresher observation already won the race
        }
        state.applyRest(limit, remaining, resetAt, observedAt);
        log.debug(
            "Seeded GitHub GraphQL rate limit from REST /rate_limit: scopeId={}, limit={}, remaining={}, resetAt={}",
            scopeId,
            limit,
            remaining,
            resetAt
        );
    }

    @Override
    public boolean hasObservation(Long scopeId) {
        return snapshot(scopeId) != null;
    }

    /**
     * Point-in-time rate-limit snapshot for a scope, for the sync-observability
     * {@code ConnectionSyncStateProvider#describe} read model.
     *
     * <p>Every value here was measured. It deliberately does NOT fall back to the assumptions that
     * {@link #getRemaining}/{@link #getLimit} return for an untracked scope: those exist so sync code has
     * a safe "assume full budget" value to throttle against, but surfacing them to an admin as a real
     * budget would misrepresent "nothing observed since the last restart" as "5000/5000 remaining". The
     * window-expiry rule is applied centrally by {@link RateLimitSnapshot#observed}.
     *
     * @param scopeId the scope to check
     * @return the snapshot, or {@code null} if nothing has been observed for this scope yet
     */
    @Nullable
    public RateLimitSnapshot snapshot(Long scopeId) {
        if (scopeId == null) {
            return null;
        }
        ScopeRateLimitState state = stateByScope.get(scopeId);
        if (state == null) {
            return null;
        }
        Instant observedAt = state.lastUpdated.get();
        if (observedAt == null) {
            return null;
        }
        return RateLimitSnapshot.observed(
            state.limit.get(),
            state.remaining.get(),
            state.resetAt.get(),
            observedAt,
            // GitHub's secondary (abuse) limits are the only 429-style back-off signal it sends, and they
            // surface on the REST/GraphQL transport rather than in the rateLimit field this tracker reads.
            // Left null until GitHubExceptionClassifier's secondary-limit detection is wired through.
            null
        );
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

        // NaN, not 0, while unobserved: a gauge is a measurement too, and 0 would read as "exhausted".
        Gauge.builder("github.graphql.ratelimit.points.remaining", state, s -> asDouble(s.remaining.get()))
            .tags(tags)
            .description("GitHub GraphQL API rate limit points remaining")
            .register(meterRegistry);

        Gauge.builder("github.graphql.ratelimit.points.limit", state, s -> asDouble(s.limit.get()))
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

    /** Renders an unobserved value as NaN rather than a number that would be read as a measurement. */
    private static double asDouble(@Nullable Integer value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    /**
     * Rate limit state for a single scope. All fields are atomic for thread-safe concurrent access, and
     * every observable field starts <b>null</b>: nothing here is a fact until GitHub says so. The previous
     * seeds (5000/5000 and {@code now()+1h}) were the bug — even where a same-tick {@code update()} masked
     * them, any new caller of {@code getOrCreateState} would have resurrected the full fabrication.
     */
    private static class ScopeRateLimitState {

        final AtomicReference<Integer> remaining = new AtomicReference<>();
        final AtomicReference<Integer> limit = new AtomicReference<>();
        final AtomicInteger used = new AtomicInteger(0);
        final AtomicInteger lastQueryCost = new AtomicInteger(0);
        final AtomicReference<Instant> resetAt = new AtomicReference<>();
        final AtomicReference<Instant> lastUpdated = new AtomicReference<>();

        /** Records a REST {@code GET /rate_limit} observation of the {@code graphql} resource. */
        void applyRest(int newLimit, int newRemaining, @Nullable Instant newResetAt, Instant observedAt) {
            remaining.set(newRemaining);
            limit.set(newLimit);
            used.set(Math.max(0, newLimit - newRemaining));
            if (newResetAt != null) {
                resetAt.set(newResetAt);
            }
            lastUpdated.set(observedAt);
        }

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

package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-scope Outline API rate-limit tracker, populated from the {@code RateLimit-*} response headers Outline
 * returns on every call (formally the {@code RateLimited} response in Outline's OpenAPI spec). It mirrors
 * {@link de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker} and feeds the same
 * {@link RateLimitSnapshot} SPI the Outline admin page renders, but is purely observational: Outline
 * throttling is enforced by the {@code Retry-After}-honoring 429 handling in {@link OutlineApiClient}, so
 * this class never blocks or paces — it only records the last-seen budget.
 *
 * <h2>Scope key</h2>
 * The Outline client is stateless with respect to workspace (its methods take {@code (serverUrl, token)}),
 * so the scope is the <em>normalized server origin</em> ({@code scheme://authority}) derived from the request
 * URI on the way out and from the connection's stored {@code serverUrl} at read time (see
 * {@link #scopeOf(String)}). Two workspaces pointing at the same Outline host therefore share one bucket;
 * that is an accepted approximation for a diagnostic, and matches Outline's own per-host rate window.
 *
 * <h2>Null-until-observed</h2>
 * State is created only once real headers arrive, so {@link #snapshot(String)} returns {@code null} until
 * the first API call since the last restart — the UI renders that as "–", exactly like GitHub/GitLab.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@WorkspaceAgnostic(
    "System-wide rate-limit tracking — records Outline API limits per server origin across all workspaces"
)
public class OutlineRateLimitTracker {

    static final String HEADER_LIMIT = "RateLimit-Limit";
    static final String HEADER_REMAINING = "RateLimit-Remaining";
    static final String HEADER_RESET = "RateLimit-Reset";

    private static final String METRIC_PREFIX = "outline.api.ratelimit";

    /** Below this magnitude a {@code RateLimit-Reset} value is read as delta-seconds, above it as epoch-seconds. */
    private static final long EPOCH_THRESHOLD_SECONDS = 1_000_000L;

    /** Inactive scope state is evicted after this idle period to bound memory. */
    private static final Duration EVICTION_THRESHOLD = Duration.ofHours(24);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, ScopeState> stateByScope = new ConcurrentHashMap<>();

    public OutlineRateLimitTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Normalizes a server URL (or full request URI) to its origin scope key {@code scheme://authority}.
     * Falls back to the trimmed input when the URL cannot be parsed, so a malformed value never crashes the
     * exchange filter — it simply keys its own bucket.
     */
    @Nullable
    public static String scopeOf(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
            return (uri.getScheme() + "://" + uri.getAuthority()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    /** Records the {@code RateLimit-*} budget for a scope; a no-op when the scope or both count headers are absent. */
    public void updateFromHeaders(@Nullable String scope, @Nullable HttpHeaders headers) {
        if (scope == null || headers == null) {
            return;
        }
        String limitStr = headers.getFirst(HEADER_LIMIT);
        String remainingStr = headers.getFirst(HEADER_REMAINING);
        if (limitStr == null && remainingStr == null) {
            return;
        }
        try {
            ScopeState state = getOrCreateState(scope);
            if (limitStr != null) {
                state.limit.set(Integer.parseInt(limitStr.trim()));
            }
            if (remainingStr != null) {
                state.remaining.set(Integer.parseInt(remainingStr.trim()));
            }
            Instant reset = parseReset(headers.getFirst(HEADER_RESET));
            if (reset != null) {
                state.resetAt.set(reset);
            }
            state.lastUpdated.set(Instant.now());
            if (log.isDebugEnabled()) {
                log.debug(
                    "outline.ratelimit: scope={}, remaining={}, limit={}, resetAt={}",
                    scope,
                    state.remaining.get(),
                    state.limit.get(),
                    state.resetAt.get()
                );
            }
        } catch (NumberFormatException e) {
            log.debug("outline.ratelimit: could not parse rate-limit headers for scope {}: {}", scope, e.getMessage());
        }
    }

    /**
     * Point-in-time budget for the sync-observability provider, or {@code null} if this scope has never had a
     * real rate-limit header observed since the last restart (state is only created by
     * {@link #updateFromHeaders}, so {@code null} already means "never observed").
     */
    @Nullable
    public RateLimitSnapshot snapshot(@Nullable String scope) {
        if (scope == null) {
            return null;
        }
        ScopeState state = stateByScope.get(scope);
        if (state == null) {
            return null;
        }
        return new RateLimitSnapshot(state.limit.get(), state.remaining.get(), state.resetAt.get());
    }

    /** Number of tracked scopes (monitoring/debugging). */
    public int getTrackedScopeCount() {
        return stateByScope.size();
    }

    /**
     * Interprets Outline's {@code RateLimit-Reset}. Outline (via express-rate-limit) emits delta-seconds, but
     * the spec describes it as an absolute timestamp, so we accept both: small values are seconds-from-now,
     * large values epoch-seconds, and an ISO-8601 instant is parsed as a last resort. Unparseable → {@code null}.
     */
    @Nullable
    private static Instant parseReset(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            long seconds = Long.parseLong(value);
            if (seconds < 0) {
                return null;
            }
            return seconds < EPOCH_THRESHOLD_SECONDS
                ? Instant.now().plusSeconds(seconds)
                : Instant.ofEpochSecond(seconds);
        } catch (NumberFormatException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeException e2) {
                return null;
            }
        }
    }

    @Scheduled(fixedRate = 3_600_000) // 1 hour
    void evictStaleEntries() {
        Instant threshold = Instant.now().minus(EVICTION_THRESHOLD);
        List<String> toEvict = new ArrayList<>();
        for (Map.Entry<String, ScopeState> entry : stateByScope.entrySet()) {
            Instant lastUpdated = entry.getValue().lastUpdated.get();
            if (lastUpdated != null && lastUpdated.isBefore(threshold)) {
                toEvict.add(entry.getKey());
            }
        }
        for (String scope : toEvict) {
            stateByScope.remove(scope);
            deregisterMetrics(scope);
            log.info("outline.ratelimit: evicted stale scope state: scope={}", scope);
        }
    }

    private ScopeState getOrCreateState(String scope) {
        return stateByScope.computeIfAbsent(scope, key -> {
            ScopeState state = new ScopeState();
            registerMetrics(key, state);
            return state;
        });
    }

    private void registerMetrics(String scope, ScopeState state) {
        Tags tags = Tags.of("scope", scope);
        Gauge.builder(METRIC_PREFIX + ".remaining", state.remaining, AtomicInteger::get)
            .tags(tags)
            .description("Outline API rate-limit requests remaining in the current window")
            .register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + ".limit", state.limit, AtomicInteger::get)
            .tags(tags)
            .description("Outline API rate-limit requests allowed per window")
            .register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + ".seconds_until_reset", state, s -> {
            Instant reset = s.resetAt.get();
            if (reset == null) {
                return 0.0;
            }
            return Math.max(0, Duration.between(Instant.now(), reset).getSeconds());
        })
            .tags(tags)
            .description("Seconds until the Outline API rate-limit window resets")
            .register(meterRegistry);
    }

    private void deregisterMetrics(String scope) {
        List<Meter.Id> toRemove = new ArrayList<>();
        meterRegistry
            .getMeters()
            .forEach(meter -> {
                if (meter.getId().getName().startsWith(METRIC_PREFIX) && scope.equals(meter.getId().getTag("scope"))) {
                    toRemove.add(meter.getId());
                }
            });
        for (Meter.Id id : toRemove) {
            meterRegistry.remove(id);
        }
    }

    /** Per-scope state; atomic fields for thread-safe concurrent updates from the exchange filter. */
    private static final class ScopeState {

        final AtomicInteger remaining = new AtomicInteger(0);
        final AtomicInteger limit = new AtomicInteger(0);
        final AtomicReference<Instant> resetAt = new AtomicReference<>();
        final AtomicReference<Instant> lastUpdated = new AtomicReference<>(Instant.now());
    }
}

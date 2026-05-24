package de.tum.cit.aet.hephaestus.integration.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * Per-kind rate-limit observability and throttling.
 *
 * <p>Reshaped from the prior bucket-headers-only contract — Slack returns no
 * remaining/limit on success, only {@code Retry-After} on 429. Some vendors
 * rate-limit per-method per-workspace ({@code operationScope}), others globally.
 *
 * <p>Vendor parsing of HTTP/GraphQL responses into {@link RateLimitHeaders} lives in
 * vendor packages ({@code integration/<kind>/ratelimit/}). The interface holds no
 * vendor types — agent A2's structural fix for the original {@code RateLimitTracker}
 * leak.
 */
public interface RateLimitTracker {

    /** Vendor binding — every per-kind SPI in this framework declares its {@link IntegrationKind}. */
    IntegrationKind kind();

    void recordSuccess(IntegrationRef ref, @Nullable RateLimitHeaders headers, @Nullable String operationScope);

    void recordThrottled(IntegrationRef ref, Duration retryAfter, @Nullable String operationScope);

    Optional<RateLimitState> getState(IntegrationRef ref, @Nullable String operationScope);

    Duration recommendedDelay(IntegrationRef ref, @Nullable String operationScope);

    record RateLimitHeaders(int remaining, int limit, @Nullable Instant resetAt) {
    }

    record RateLimitState(
        @Nullable Integer remaining,
        @Nullable Integer limit,
        @Nullable Instant resetAt,
        @Nullable Instant blockedUntil,
        Severity severity,
        Instant updatedAt
    ) {
    }

    enum Severity {
        OK,
        LOW,
        CRITICAL,
        THROTTLED
    }
}

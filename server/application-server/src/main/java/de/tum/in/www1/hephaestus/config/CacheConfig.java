package de.tum.in.www1.hephaestus.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for the application using Caffeine for TTL support and metrics.
 *
 * <p>Registers named caches for:
 * <ul>
 *   <li>{@code contributors} - Contributor list cache (1 hour TTL, also evicted by CacheScheduler)</li>
 *   <li>{@code pullRequestTemplates} - PR template cache (1 hour TTL)</li>
 * </ul>
 *
 * <p>All caches expose metrics via Micrometer:
 * <ul>
 *   <li>{@code cache.gets} - hit/miss count by result tag</li>
 *   <li>{@code cache.evictions} - eviction count</li>
 *   <li>{@code cache.size} - current entry count</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    /** TTL for long-lived caches (contributors, PR templates) in seconds. */
    private static final long LONG_CACHE_TTL_SECONDS = 3600; // 1 hour

    /** Maximum entries per cache to prevent memory issues. */
    private static final long MAX_CACHE_SIZE = 1000;

    @Bean
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        // Contributors cache: 1 hour TTL, also evicted by CacheScheduler
        CaffeineCache contributorsCache = buildCache("contributors", LONG_CACHE_TTL_SECONDS, meterRegistry);

        // PR templates cache: 1 hour TTL
        CaffeineCache pullRequestTemplatesCache = buildCache(
            "pullRequestTemplates",
            LONG_CACHE_TTL_SECONDS,
            meterRegistry
        );

        cacheManager.setCaches(Arrays.asList(contributorsCache, pullRequestTemplatesCache));

        return cacheManager;
    }

    /**
     * Builds a Caffeine cache with TTL, size limit, and metrics registration.
     *
     * @param name cache name (used for metrics tags)
     * @param ttlSeconds time-to-live after write in seconds
     * @param meterRegistry registry for cache metrics
     * @return configured CaffeineCache
     */
    private CaffeineCache buildCache(String name, long ttlSeconds, MeterRegistry meterRegistry) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .maximumSize(MAX_CACHE_SIZE)
            .recordStats()
            .build();

        // Register cache metrics with Micrometer
        CaffeineCacheMetrics.monitor(meterRegistry, cache, name, List.of());

        return new CaffeineCache(name, cache);
    }
}

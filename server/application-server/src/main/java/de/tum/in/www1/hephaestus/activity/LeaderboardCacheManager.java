package de.tum.in.www1.hephaestus.activity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Workspace-scoped cache management for leaderboard data.
 *
 * <p>Provides granular cache invalidation by workspace ID instead of clearing
 * all entries across all workspaces. This is more efficient for multi-tenant
 * deployments where activity in one workspace shouldn't invalidate cache
 * for other workspaces.
 *
 * <h3>Design Note</h3>
 * <p>Spring's {@code @CacheEvict} annotation cannot do pattern-based key eviction,
 * so we use programmatic cache access with key tracking. This trades some memory
 * for more surgical invalidation.
 */
@Component
public class LeaderboardCacheManager {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardCacheManager.class);
    private static final String CACHE_NAME = "leaderboardXp";
    private static final int MAX_TRACKED_WORKSPACES = 10000;
    private static final int MAX_KEYS_PER_WORKSPACE = 1000;

    private final CacheManager cacheManager;
    private final Counter cacheEvictionsCounter;
    private final Counter cacheEvictAllCounter;
    private final Counter cacheKeyRegistrationsCounter;
    private final Counter cacheKeyEvictedCounter;

    /**
     * Track cache keys by workspace for targeted eviction.
     * Concurrent set for thread safety.
     * BOUNDED to prevent memory leaks in production.
     */
    private final ConcurrentHashMap<Long, Set<String>> keysByWorkspace = new ConcurrentHashMap<>();

    public LeaderboardCacheManager(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;

        this.cacheEvictionsCounter = Counter.builder("leaderboard.cache.evictions")
            .description("Number of workspace-level cache evictions")
            .register(meterRegistry);
        this.cacheEvictAllCounter = Counter.builder("leaderboard.cache.evict_all")
            .description("Number of full cache clears")
            .register(meterRegistry);
        this.cacheKeyRegistrationsCounter = Counter.builder("leaderboard.cache.key_registrations")
            .description("Number of cache keys registered")
            .register(meterRegistry);
        this.cacheKeyEvictedCounter = Counter.builder("leaderboard.cache.keys_evicted")
            .description("Number of individual cache keys evicted")
            .register(meterRegistry);

        // Gauges for memory pressure visibility
        Gauge.builder("leaderboard.cache.tracked_workspaces", keysByWorkspace, ConcurrentHashMap::size)
            .description("Number of workspaces with tracked cache keys")
            .register(meterRegistry);
        Gauge.builder("leaderboard.cache.total_tracked_keys", this, LeaderboardCacheManager::getTotalTrackedKeys)
            .description("Total number of tracked cache keys across all workspaces")
            .register(meterRegistry);
    }

    /**
     * Register a cache key for a workspace.
     *
     * <p>Called when caching a leaderboard query result.
     * BOUNDED: Limits tracked workspaces and keys per workspace to prevent OOM.
     *
     * @param workspaceId the workspace
     * @param cacheKey the cache key used
     */
    public void registerKey(Long workspaceId, String cacheKey) {
        // Prevent unbounded growth - memory safety
        if (keysByWorkspace.size() >= MAX_TRACKED_WORKSPACES && !keysByWorkspace.containsKey(workspaceId)) {
            log.warn("Workspace limit reached for cache tracking: maxWorkspaces={}", MAX_TRACKED_WORKSPACES);
            return;
        }

        Set<String> keys = keysByWorkspace.computeIfAbsent(workspaceId, k -> ConcurrentHashMap.newKeySet());
        if (keys.size() >= MAX_KEYS_PER_WORKSPACE) {
            log.warn(
                "Key limit reached for cache tracking: scopeId={}, maxKeys={}",
                workspaceId,
                MAX_KEYS_PER_WORKSPACE
            );
            return;
        }

        keys.add(cacheKey);
        cacheKeyRegistrationsCounter.increment();
    }

    /**
     * Evict all cache entries for a specific workspace.
     *
     * <p>This is more efficient than {@code allEntries = true} because it only
     * invalidates entries for the affected workspace.
     *
     * @param workspaceId the workspace to evict cache for
     */
    public void evictWorkspace(Long workspaceId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            log.debug("Skipped eviction, cache not found: cacheName={}", CACHE_NAME);
            return;
        }

        Set<String> keys = keysByWorkspace.remove(workspaceId);
        if (keys == null || keys.isEmpty()) {
            log.debug("Skipped eviction, no cached keys: scopeId={}", workspaceId);
            return;
        }

        int evictedCount = 0;
        for (String key : keys) {
            cache.evict(key);
            evictedCount++;
        }
        cacheEvictionsCounter.increment();
        cacheKeyEvictedCounter.increment(evictedCount);
        log.info("Evicted leaderboard cache: scopeId={}, keysEvicted={}", workspaceId, evictedCount);
    }

    /**
     * Evict all entries (fallback for bulk operations).
     */
    public void evictAll() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            int totalKeys = getTotalTrackedKeys();
            cache.clear();
            keysByWorkspace.clear();
            cacheEvictAllCounter.increment();
            log.info("Evicted all leaderboard cache: keysCleared={}", totalKeys);
        }
    }

    /**
     * Get the number of tracked workspaces (for monitoring).
     */
    public int getTrackedWorkspaceCount() {
        return keysByWorkspace.size();
    }

    /**
     * Get the number of tracked keys for a workspace (for monitoring).
     */
    public int getTrackedKeyCount(Long workspaceId) {
        Set<String> keys = keysByWorkspace.get(workspaceId);
        return keys == null ? 0 : keys.size();
    }

    /**
     * Get the total number of tracked keys across all workspaces.
     */
    public int getTotalTrackedKeys() {
        return keysByWorkspace.values().stream().mapToInt(Set::size).sum();
    }
}

package de.tum.in.www1.hephaestus.analytics.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class AnalyticsCacheConfig {

    private final AnalyticsProperties analyticsProperties;

    @Bean
    public CacheManager analyticsCacheManager() {
        return new ConcurrentMapCacheManager() {
            @Override
            protected ConcurrentMapCache createConcurrentMapCache(String name) {
                return new ConcurrentMapCache(
                    name,
                    CacheBuilder.newBuilder()
                        .expireAfterWrite(getCacheTtl(name), TimeUnit.MINUTES)
                        .build().asMap(),
                    false);
            }
        };
    }

    private long getCacheTtl(String cacheName) {
        AnalyticsProperties.Cache cacheProps = analyticsProperties.getCache();
        return switch (cacheName) {
            case "teamMetrics" -> cacheProps.getTeamMetricsTtlMinutes();
            case "analysisResults" -> cacheProps.getAnalysisResultsTtlMinutes();
            case "recommendations" -> cacheProps.getRecommendationsTtlMinutes();
            default -> 30; // Default TTL
        };
    }
}
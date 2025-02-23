package de.tum.in.www1.hephaestus.analytics.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
public class AnalyticsCacheConfig {

    @Bean
    public CacheManager analyticsCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("teamMetrics"),
            new ConcurrentMapCache("analysisResults"),
            new ConcurrentMapCache("recommendations")
        ));
        return cacheManager;
    }
}
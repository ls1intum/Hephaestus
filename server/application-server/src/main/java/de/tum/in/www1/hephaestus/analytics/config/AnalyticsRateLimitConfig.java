package de.tum.in.www1.hephaestus.analytics.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@RequiredArgsConstructor
public class AnalyticsRateLimitConfig {

    private final AnalyticsProperties analyticsProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public Map<String, Bucket> buckets() {
        return buckets;
    }

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::newBucket);
    }

    private Bucket newBucket(String key) {
        AnalyticsProperties.RateLimit rateLimitProps = analyticsProperties.getRateLimit();
        return Bucket.builder()
            .addLimit(Bandwidth.classic(
                rateLimitProps.getCapacity(),
                Refill.intervally(rateLimitProps.getRefillTokens(), 
                                Duration.ofMinutes(rateLimitProps.getRefillMinutes()))))
            .build();
    }
}
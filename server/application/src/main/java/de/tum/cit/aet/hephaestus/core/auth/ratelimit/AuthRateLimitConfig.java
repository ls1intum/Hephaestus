package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the {@link AuthRateLimitFilter} over the {@link BucketResolver} that {@link BucketResolverConfig}
 * provides on every role, and the sweep that keeps its Postgres store bounded.
 */
@ConditionalOnServerRole
@Configuration
public class AuthRateLimitConfig {

    /**
     * Prunes expired buckets so {@code auth_rate_limit_bucket} stays bounded. Only present on the
     * Postgres-backed path (the in-JVM fallback bounds itself).
     */
    @Bean
    @ConditionalOnProperty(prefix = "hephaestus.auth.rate-limit", name = "postgres-backed", matchIfMissing = true)
    AuthRateLimitBucketSweeper authRateLimitBucketSweeper(ProxyManager<String> proxyManager) {
        return new AuthRateLimitBucketSweeper(proxyManager);
    }

    @Bean
    AuthRateLimitFilter authRateLimitFilter(
            AuthRateLimitProperties properties,
            BucketResolver bucketResolver,
            ObjectMapper objectMapper,
            AuthMetrics metrics) {
        return new AuthRateLimitFilter(properties, bucketResolver, objectMapper, metrics);
    }
}

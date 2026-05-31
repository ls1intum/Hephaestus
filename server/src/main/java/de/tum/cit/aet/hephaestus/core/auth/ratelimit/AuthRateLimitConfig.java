package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the {@link AuthRateLimitFilter} and its storage backend.
 *
 * <h2>Backend choice — shared vs. per-replica</h2>
 *
 * <p>The primary path is a Postgres {@code ProxyManager} (SELECT … FOR UPDATE on the
 * {@code auth_rate_limit_bucket} table created by Liquibase migration
 * {@code 1779850000000_auth_rate_limit_bucket.xml}). Buckets are persisted, so the limits are
 * <strong>shared across all replicas</strong> with no extra infrastructure — we reuse the existing
 * DataSource (no Redis; ADR 0017 constraint). This bean is only created when a {@link DataSource}
 * is present (i.e. a real Postgres-backed boot), guarded by
 * {@code hephaestus.auth.rate-limit.postgres-backed=true} (default).
 *
 * <p><strong>Trade-off / fallback:</strong> when no Postgres DataSource is available (the
 * {@code specs} profile, worker-only pods, the H2 test context, or {@code postgres-backed=false}),
 * an in-JVM {@code ConcurrentHashMap}-backed resolver is used instead. In that mode the limits are
 * <strong>per-replica</strong>: N replicas allow up to N× the configured rate cluster-wide. This is
 * acceptable for those non-production contexts but would be a regression in a multi-replica
 * production deployment — production MUST run Postgres-backed. The active mode is logged at startup.
 */
@Configuration
public class AuthRateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitConfig.class);

    /** Table backing the distributed buckets (see the matching Liquibase changelog). */
    static final String BUCKET_TABLE = "auth_rate_limit_bucket";

    /**
     * Postgres-backed, cluster-shared bucket store. Created only on a Postgres-backed boot. The
     * builder defaults to {@code BIGINT} keys; we remap to {@code STRING} because our keys are
     * heterogeneous (IP strings + decimal account ids).
     */
    @Bean
    @ConditionalOnProperty(prefix = "hephaestus.auth.rate-limit", name = "postgres-backed", matchIfMissing = true)
    @ConditionalOnMissingBean(BucketResolver.class)
    BucketResolver postgresBucketResolver(DataSource dataSource) {
        ProxyManager<String> proxyManager = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
            .primaryKeyMapper(PrimaryKeyMapper.STRING)
            .table(BUCKET_TABLE)
            .build();
        log.info("Auth rate limiting: Postgres-backed (table={}) — limits SHARED across replicas.", BUCKET_TABLE);
        return (key, config) -> proxyManager.getProxy(key, () -> config);
    }

    /**
     * In-JVM fallback. PER-REPLICA limits — see the class Javadoc trade-off. Activated only when the
     * Postgres bean backed off (no DataSource / {@code postgres-backed=false}).
     */
    @Bean
    @ConditionalOnMissingBean(BucketResolver.class)
    BucketResolver inMemoryBucketResolver() {
        log.warn(
            "Auth rate limiting: in-JVM fallback — limits are PER-REPLICA, NOT shared across the " +
                "cluster. Acceptable for dev / specs / worker-only pods; production must run " +
                "Postgres-backed (hephaestus.auth.rate-limit.postgres-backed=true with a DataSource)."
        );
        var store = new java.util.concurrent.ConcurrentHashMap<String, io.github.bucket4j.Bucket>();
        return (key, config) ->
            store.computeIfAbsent(key, k ->
                io.github.bucket4j.Bucket.builder().addLimit(config.getBandwidths()[0]).build()
            );
    }

    @Bean
    AuthRateLimitFilter authRateLimitFilter(
        AuthRateLimitProperties properties,
        BucketResolver bucketResolver,
        ObjectMapper objectMapper,
        de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics metrics
    ) {
        return new AuthRateLimitFilter(properties, bucketResolver, objectMapper, metrics);
    }
}

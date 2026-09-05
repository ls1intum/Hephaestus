package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@link BucketResolver} every rate limiter shares — the auth endpoints on the server role and
 * the sandbox gateway on the worker role — and therefore ungated, unlike the rest of {@code core.auth}.
 *
 * <h2>Backend choice — shared vs. per-replica</h2>
 *
 * <p>The primary path is a Postgres {@code ProxyManager} (SELECT … FOR UPDATE on the
 * {@code auth_rate_limit_bucket} table created by the consolidated auth Liquibase changelog).
 * Buckets are persisted, so the limits are <strong>shared across all replicas</strong> with no extra
 * infrastructure — we reuse the existing DataSource (no Redis; ADR 0017 constraint). These beans are
 * created whenever {@code hephaestus.auth.rate-limit.postgres-backed=true} (the default) and
 * <em>require</em> a {@link DataSource} — there is no {@code @ConditionalOnBean(DataSource)} back-off,
 * so a boot with no DataSource must explicitly set {@code postgres-backed=false} to select the in-JVM
 * fallback below (otherwise context startup fails on the unsatisfied {@code DataSource} dependency).
 *
 * <p><strong>Trade-off / fallback:</strong> when {@code postgres-backed=false} (the {@code specs}
 * profile, the H2 test context, and any DataSource-less boot set it), an in-JVM resolver is used
 * instead. In that mode the limits are <strong>per-replica</strong>: N replicas allow up to N× the
 * configured rate cluster-wide. This is acceptable for those non-production contexts but would be a
 * regression in a multi-replica production deployment — production MUST run Postgres-backed. The
 * active mode is logged at startup.
 */
@Configuration
public class BucketResolverConfig {

    private static final Logger log = LoggerFactory.getLogger(BucketResolverConfig.class);

    /** Table backing the distributed buckets (see the matching Liquibase changelog). */
    static final String BUCKET_TABLE = "auth_rate_limit_bucket";

    /** Far above any pod's working set of live principals; bounds a pathological key stream. */
    static final int IN_MEMORY_MAX_BUCKETS = 10_000;

    /**
     * Postgres-backed, cluster-shared bucket store. Created only on a Postgres-backed boot. The
     * builder defaults to {@code BIGINT} keys; we remap to {@code STRING} because our keys are
     * heterogeneous (IP strings + decimal account ids).
     *
     * <p>{@code expirationAfterWrite} populates the {@code expires_at} column so
     * {@link AuthRateLimitBucketSweeper} can prune stale rows — without it the table grows without
     * bound on the pre-auth, IP-keyed hot path (one permanent row per distinct/rotating client IP).
     * {@code basedOnTimeForRefillingBucketUpToMax} keeps a bucket until it would have fully refilled
     * to max — i.e. until the limit no longer constrains anyone — so eviction never resets a still
     * meaningful limit; the one-minute argument is slack on top of that refill time.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hephaestus.auth.rate-limit", name = "postgres-backed", matchIfMissing = true)
    ProxyManager<String> authRateLimitProxyManager(DataSource dataSource) {
        ProxyManager<String> proxyManager = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table(BUCKET_TABLE)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
        log.info("Rate limiting: Postgres-backed (table={}) — limits SHARED across replicas.", BUCKET_TABLE);
        return proxyManager;
    }

    @Bean
    @ConditionalOnProperty(prefix = "hephaestus.auth.rate-limit", name = "postgres-backed", matchIfMissing = true)
    @ConditionalOnMissingBean(BucketResolver.class)
    BucketResolver postgresBucketResolver(ProxyManager<String> proxyManager) {
        return (key, config) -> proxyManager.getProxy(key, () -> config);
    }

    /**
     * In-JVM fallback. PER-REPLICA limits — see the class Javadoc trade-off. Activated only when
     * {@code postgres-backed=false} (the Postgres beans above require a DataSource and do not back off
     * on their own). Bounded by {@link #IN_MEMORY_MAX_BUCKETS} and by an hour's idleness, which is
     * longer than any limit's refill period.
     */
    @Bean
    @ConditionalOnMissingBean(BucketResolver.class)
    BucketResolver inMemoryBucketResolver() {
        log.warn("Rate limiting: in-JVM fallback — limits are PER-REPLICA, NOT shared across the "
                + "cluster. Acceptable for dev / specs; production must run Postgres-backed "
                + "(hephaestus.auth.rate-limit.postgres-backed=true with a DataSource).");
        Cache<String, Bucket> buckets = Caffeine.newBuilder()
                .maximumSize(IN_MEMORY_MAX_BUCKETS)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
        return (key, config) -> buckets.get(
                key,
                ignored -> Bucket.builder().addLimit(config.getBandwidths()[0]).build());
    }
}

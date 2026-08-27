package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

/**
 * Resolves the token bucket for a rate-limit key. The single seam between {@link AuthRateLimitFilter}
 * and the storage backend:
 *
 * <ul>
 *   <li>Production wires a Postgres {@code ProxyManager}-backed implementation so buckets are
 *       SHARED across replicas (SELECT … FOR UPDATE; no Redis).</li>
 *   <li>Unit tests pass an in-JVM implementation so the key-derivation / limit logic is testable
 *       without a database.</li>
 * </ul>
 *
 * <p>The returned {@link Bucket} is a thin handle; callers invoke
 * {@code tryConsumeAndReturnRemaining(1)} per request.
 */
@FunctionalInterface
public interface BucketResolver {
    /**
     * @param key    fully-qualified bucket key (limit name + principal), already namespaced by the
     *               filter so distinct endpoints never collide.
     * @param config the bandwidth configuration for this key's limit.
     */
    Bucket resolve(String key, BucketConfiguration config);
}

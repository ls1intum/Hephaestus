package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the token bucket for a rate-limit key. The single seam between a rate-limit filter and the
 * storage backend:
 *
 * <ul>
 *   <li>Production wires a Postgres {@code ProxyManager}-backed implementation so buckets are
 *       SHARED across replicas (SELECT … FOR UPDATE; no Redis).</li>
 *   <li>Unit tests pass an in-JVM implementation so the key-derivation / limit logic is testable
 *       without a database.</li>
 * </ul>
 *
 * <p>The returned {@link Bucket} is a thin handle; callers consume through {@link #tryConsume}.
 */
@FunctionalInterface
public interface BucketResolver {
    /**
     * @param key    fully-qualified bucket key (limit name + principal), already namespaced by the
     *               filter so distinct endpoints never collide.
     * @param config the bandwidth configuration for this key's limit.
     */
    Bucket resolve(String key, BucketConfiguration config);

    /**
     * One token for {@code key}, or {@code null} when the store behind the bucket could not be
     * reached. {@code onStoreFailure} is handed the cause, so each limiter records and logs the
     * outage in its own module's terms and then decides fail-open or fail-closed for its own callers.
     *
     * <p>Shared because that decision is the only part a limiter may write for itself: a store
     * exception left to escape a filter is dispatched to the container's error handling, where the
     * caller gets whatever that chain answers an unexpected error with instead of a status it can act
     * on.
     */
    @Nullable
    default ConsumptionProbe tryConsume(
            String key, BucketConfiguration config, Consumer<RuntimeException> onStoreFailure) {
        try {
            return resolve(key, config).tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            onStoreFailure.accept(e);
            return null;
        }
    }
}

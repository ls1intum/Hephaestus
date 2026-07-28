package de.tum.cit.aet.hephaestus.integration.scm.github.common;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The write side of GitHub rate-limit tracking: the narrow capability {@link GitHubRestRateLimitSeeder}
 * needs to feed a real REST {@code GET /rate_limit} observation into the tracker and to ask whether that
 * is even necessary yet.
 *
 * <p>Kept separate from {@link RateLimitTracker} (the throttling-decision and reporting API) so the
 * seeder depends only on what it uses, per the ISP rule the architecture tests enforce.
 * {@link ScopedRateLimitTracker} implements both.
 */
public interface RateLimitObservationSink {
    /**
     * Records a real observation taken from REST {@code GET /rate_limit}'s {@code resources.graphql}
     * entry, so the true per-installation ceiling is known before the first GraphQL call of a process.
     * That endpoint does not count against the rate limit, so this costs nothing.
     *
     * @param scopeId    the scope the observation belongs to
     * @param limit      the reported window ceiling
     * @param remaining  the reported remaining budget
     * @param resetAt    the reported window end, or null if absent
     * @param observedAt when the response was received; a stale observation must not overwrite a fresher one
     */
    void updateFromRestRateLimit(Long scopeId, int limit, int remaining, @Nullable Instant resetAt, Instant observedAt);

    /**
     * Whether anything has actually been observed for this scope — i.e. whether a snapshot would exist.
     * Lets the REST seeder skip scopes that already have measured data.
     */
    boolean hasObservation(Long scopeId);
}

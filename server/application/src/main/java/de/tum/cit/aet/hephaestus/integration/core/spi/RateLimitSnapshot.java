package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Point-in-time record of what a vendor actually told us about its rate limit.
 *
 * <h2>The honesty rule</h2>
 * A snapshot <b>exists only if something was observed</b>, and every field in it is either an observed
 * value or {@code null}. The record itself is the "observed" discriminator: no observation, no snapshot,
 * and the UI omits the row entirely. Nothing here may be seeded, assumed, or derived from optimistic
 * bookkeeping — the internal throttling defaults that trackers hand to sync code (GitHub's
 * {@code DEFAULT_LIMIT}, GitLab's {@code DEFAULT_RATE_LIMIT}) live behind the
 * {@code getRemaining()/getLimit()} decision APIs and must be structurally unable to reach this record.
 *
 * <p>Not persisted across restarts — a {@code null} at the {@link ConnectionSyncStateProvider} call site
 * means "nothing observed since the last restart", which is a first-class, supported display state. It is
 * also the permanently correct state for vendors that cannot report a budget at all: Slack sends no budget
 * headers (per-method tiers only), a healthy Outline emits {@code RateLimit-*} only on a 429, and a
 * self-managed GitLab has request throttling disabled by default and therefore sends no headers.
 *
 * @param limit          window ceiling, if the vendor reported one
 * @param remaining      remaining budget, if reported AND still inside the observed window
 * @param resetAt        window end, if reported AND still in the future
 * @param observedAt     when the underlying vendor response was seen — a snapshot is an observation, so
 *                       this is always present
 * @param throttledUntil an observed 429's {@code observedAt + Retry-After}; {@code null} if the vendor has
 *                       never told us to back off
 */
public record RateLimitSnapshot(
    @Nullable Integer limit,
    @Nullable Integer remaining,
    @Nullable Instant resetAt,
    @NonNull Instant observedAt,
    @Nullable Instant throttledUntil
) {
    /**
     * The sanctioned construction path for every tracker — applies the shared <b>window-expiry rule</b> so
     * no provider has to (and so none can do it differently).
     *
     * <p>Once an observed {@code resetAt} has passed, the window has rolled over and the budget we measured
     * inside it is no longer a fact about now: {@code remaining} and {@code resetAt} are reported as
     * {@code null}. {@code limit} survives, because a ceiling is window-invariant — it is still the real
     * ceiling this instance reported, and {@code observedAt} says how old that reading is.
     *
     * <p>{@code throttledUntil} is passed through even when it lies in the past; the UI renders it only
     * while it is still in the future, which keeps the invariant here trivial.
     */
    public static RateLimitSnapshot observed(
        @Nullable Integer limit,
        @Nullable Integer remaining,
        @Nullable Instant resetAt,
        Instant observedAt,
        @Nullable Instant throttledUntil
    ) {
        boolean windowClosed = resetAt != null && !Instant.now().isBefore(resetAt);
        return new RateLimitSnapshot(
            limit,
            windowClosed ? null : remaining,
            windowClosed ? null : resetAt,
            observedAt,
            throttledUntil
        );
    }
}

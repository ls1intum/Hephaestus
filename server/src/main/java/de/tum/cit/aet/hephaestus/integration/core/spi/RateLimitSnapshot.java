package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Point-in-time snapshot of a vendor's rate-limit budget, read from an existing in-memory tracker
 * (e.g. GitHub's {@code ScopedRateLimitTracker}, GitLab's {@code GitLabRateLimitTracker}). Not
 * persisted across restarts — {@code null} at the {@link ConnectionSyncStateProvider} call site means
 * "unknown until the first API call since the last restart", which the UI renders as "–".
 */
public record RateLimitSnapshot(int limit, int remaining, @Nullable Instant resetAt) {}

package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Connection-level sync-observability snapshot returned by {@link ConnectionSyncStateProvider#describe}.
 *
 * @param webhookRegistered    whether the vendor webhook registration is present, if the integration
 *                             tracks that (e.g. GitLab: "stored webhook id present" — existence-only,
 *                             not a live probe); {@code null} when not applicable/unknown
 * @param nextScheduledSyncAt  when the next periodic reconciliation is expected to run, if the
 *                             integration's scheduler can compute one
 * @param syncInterval         the periodic reconciliation's cadence, if the integration's schedule has a
 *                             regular one. This is what makes a resource timestamp <em>judgeable</em>:
 *                             "last synced 4h ago" is only stale if the cadence is hourly, and the
 *                             caller cannot know that without this. {@code null} when the schedule is
 *                             irregular or unparseable — callers must then decline to judge staleness
 *                             rather than assume a default.
 * @param rateLimit            current rate-limit budget, or {@code null} if unknown (no call made yet
 *                             since restart) or not applicable to this integration
 * @param backfill             connection-level backfill rollup, or {@code null} if not applicable
 * @param vendorHealthDegraded    true when the integration has independent evidence of vendor-side
 *                                trouble (e.g. GitHub App installation suspended) that should push
 *                                connection health to DEGRADED even absent a failed job or errored
 *                                resource
 */
public record ConnectionSyncDetails(
    @Nullable Boolean webhookRegistered,
    @Nullable Instant nextScheduledSyncAt,
    @Nullable Duration syncInterval,
    @Nullable RateLimitSnapshot rateLimit,
    @Nullable BackfillSummary backfill,
    boolean vendorHealthDegraded
) {
    public static ConnectionSyncDetails empty() {
        return new ConnectionSyncDetails(null, null, null, null, null, false);
    }
}

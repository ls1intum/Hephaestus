package de.tum.cit.aet.hephaestus.integration.core.spi;

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
    @Nullable RateLimitSnapshot rateLimit,
    @Nullable BackfillSummary backfill,
    boolean vendorHealthDegraded
) {
    public static ConnectionSyncDetails empty() {
        return new ConnectionSyncDetails(null, null, null, null, false);
    }
}

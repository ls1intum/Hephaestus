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
 * @param degradedReason          human-readable reason, set together with {@code vendorHealthDegraded}
 * @param lastEventProcessedAt    when the last inbound webhook/NATS event was processed for this
 *                                connection, or {@code null} if none has been recorded yet. Core-owned
 *                                (from {@code ConnectionActivityRepository}) — vendor providers never
 *                                set this; {@code SyncStatusService} fills it in via {@link
 *                                #withActivity}.
 * @param lastEventType           the most recently processed event's type, alongside {@code
 *                                lastEventProcessedAt}
 */
public record ConnectionSyncDetails(
    @Nullable Boolean webhookRegistered,
    @Nullable Instant nextScheduledSyncAt,
    @Nullable RateLimitSnapshot rateLimit,
    @Nullable BackfillSummary backfill,
    boolean vendorHealthDegraded,
    @Nullable String degradedReason,
    @Nullable Instant lastEventProcessedAt,
    @Nullable String lastEventType
) {
    /**
     * Back-compat constructor for the per-vendor {@link ConnectionSyncStateProvider} implementations,
     * which know nothing about the core-owned webhook-liveness columns. {@code SyncStatusService}
     * merges those in afterward via {@link #withActivity}.
     */
    public ConnectionSyncDetails(
        @Nullable Boolean webhookRegistered,
        @Nullable Instant nextScheduledSyncAt,
        @Nullable RateLimitSnapshot rateLimit,
        @Nullable BackfillSummary backfill,
        boolean vendorHealthDegraded,
        @Nullable String degradedReason
    ) {
        this(
            webhookRegistered,
            nextScheduledSyncAt,
            rateLimit,
            backfill,
            vendorHealthDegraded,
            degradedReason,
            null,
            null
        );
    }

    /** All-unknown default — used when a kind has no {@link ConnectionSyncStateProvider} registered. */
    public static ConnectionSyncDetails empty() {
        return new ConnectionSyncDetails(null, null, null, null, false, null, null, null);
    }

    /**
     * Returns a copy with the core-owned webhook-liveness fields set. Never called by vendor
     * providers — {@code SyncStatusService} is the sole caller, right before mapping to the DTO.
     */
    public ConnectionSyncDetails withActivity(@Nullable Instant lastEventProcessedAt, @Nullable String lastEventType) {
        return new ConnectionSyncDetails(
            webhookRegistered,
            nextScheduledSyncAt,
            rateLimit,
            backfill,
            vendorHealthDegraded,
            degradedReason,
            lastEventProcessedAt,
            lastEventType
        );
    }
}

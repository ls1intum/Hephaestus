package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(description = "Unified sync-observability status for one connection")
public record ConnectionSyncStatusDTO(
    @NonNull @Schema(description = "Connection id") Long connectionId,
    @NonNull @Schema(description = "Integration kind") IntegrationKind kind,
    @NonNull @Schema(description = "Raw connection lifecycle state") IntegrationState connectionState,
    @NonNull @Schema(description = "Derived health") ConnectionHealth health,
    @Schema(description = "Most recent successful job's finish time") Instant lastSuccessfulSyncAt,
    @Schema(description = "Currently PENDING/RUNNING job, if any") SyncJobDTO activeJob,
    @Schema(description = "Most recently finished job, if any") SyncJobDTO lastJob,
    @Schema(description = "When the next periodic reconciliation is expected") Instant nextScheduledSyncAt,
    @Schema(
        description = "The periodic reconciliation's cadence in seconds, when the schedule has a regular one. " +
            "This is what makes a resource's lastSyncedAt judgeable: \"synced 4h ago\" is only stale if the " +
            "cadence is hourly, and a client cannot know that without this. Null when the schedule is " +
            "irregular or unparseable — clients must then decline to judge staleness rather than assume a " +
            "default, exactly as the server's own stale rollup does."
    )
    Long syncIntervalSeconds,
    @Schema(description = "Whether the vendor webhook registration is present; null if not applicable/unknown")
    Boolean webhookRegistered,
    @Schema(description = "When the last inbound webhook/event was processed for this connection, if any")
    Instant lastEventProcessedAt,
    @Schema(description = "The most recently processed event's type, if any") String lastEventType,
    @Schema(description = "Current rate-limit budget, if known") RateLimitSnapshotDTO rateLimit,
    @NonNull
    @Schema(
        description = "Whether this kind's runner offers an explicitly triggerable backfill pass. Reflects the " +
            "vendor capability only — the scheduled-backfill flag does not gate it, so a manual backfill stays " +
            "available while the automatic cycle is administratively paused."
    )
    Boolean backfillSupported,
    @Schema(description = "Connection-level backfill rollup, if applicable") BackfillSummaryDTO backfill,
    @NonNull @Schema(description = "Resource-level rollup") ResourceCountsDTO resourceCounts
) {}

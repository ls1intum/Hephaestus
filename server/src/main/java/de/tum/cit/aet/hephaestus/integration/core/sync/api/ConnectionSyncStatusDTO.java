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
    @Schema(description = "Whether the vendor webhook registration is present; null if not applicable/unknown")
    Boolean webhookRegistered,
    @Schema(description = "Current rate-limit budget, if known") RateLimitSnapshotDTO rateLimit,
    @Schema(description = "Connection-level backfill rollup, if applicable") BackfillSummaryDTO backfill,
    @NonNull @Schema(description = "Resource-level rollup") ResourceCountsDTO resourceCounts
) {}

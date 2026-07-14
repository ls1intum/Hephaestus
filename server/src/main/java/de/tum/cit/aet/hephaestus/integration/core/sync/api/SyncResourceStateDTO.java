package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState.Type;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(description = "One synced resource (repository / channel / collection) — unified read-model row")
public record SyncResourceStateDTO(
    @NonNull @Schema(description = "The integration's own row id") Long id,
    @NonNull @Schema(description = "Vendor-side identifier") String externalId,
    @NonNull @Schema(description = "Display name") String name,
    @NonNull @Schema(description = "Resource kind") Type type,
    @NonNull @Schema(description = "Integration-defined status string") String state,
    @Schema(description = "Last successful sync timestamp") Instant lastSyncedAt,
    @Schema(description = "Mirrored item count") Long itemCount,
    @Schema(description = "Vendor-reported upstream count, if cheaply available") Long upstreamCount,
    @Schema(description = "Last sync error, if any") String lastError,
    @Schema(description = "Per-resource backfill horizon") Instant backfillCompletedThrough,
    @Schema(description = "Per-resource backfill percent") Integer backfillPercent
) {
    public static SyncResourceStateDTO from(SyncResourceState state) {
        return new SyncResourceStateDTO(
            state.id(),
            state.externalId(),
            state.name(),
            state.type(),
            state.state(),
            state.lastSyncedAt(),
            state.itemCount(),
            state.upstreamCount(),
            state.lastError(),
            state.backfillCompletedThrough(),
            state.backfillPercent()
        );
    }
}

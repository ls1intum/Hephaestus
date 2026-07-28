package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

@Schema(description = "Manual sync trigger request body")
public record TriggerSyncJobRequestDTO(
    // @NonNull marks the field OpenAPI-required + enables static null-analysis; jakarta @NotNull is what
    // actually rejects a missing/null `type` at runtime with a 400 (rather than a misleading 409 from the
    // NOT-NULL column downstream). Both are needed.
    @NonNull
    @NotNull
    @Schema(description = "RECONCILIATION for a full re-sync, BACKFILL for historical data")
    SyncJobType type
) {}

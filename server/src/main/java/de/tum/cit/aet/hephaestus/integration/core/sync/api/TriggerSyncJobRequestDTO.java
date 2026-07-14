package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Manual sync trigger request body")
public record TriggerSyncJobRequestDTO(
    @NonNull @Schema(description = "RECONCILIATION for a full re-sync, BACKFILL for historical data") SyncJobType type
) {}

package de.tum.cit.aet.hephaestus.integration.outline.connect;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Admin health view of the workspace's ACTIVE Outline connection: whether the change-notification
 * subscription is registered, when a mirrored collection last finished a clean sync pass, how many
 * live documents the mirror holds, whether a manual reconcile is currently running, and how many
 * collections are still awaiting a clean pass or carry a sync error. This is the status line the
 * admin connect card renders — and the monitor resource a manual sync's 202 {@code Location} points at.
 */
@Schema(description = "Health of the workspace's active Outline connection")
public record OutlineConnectionStatusDTO(
    @NonNull
    @Schema(description = "Whether the Outline change-notification webhook subscription is registered")
    Boolean webhookRegistered,
    @Schema(description = "When a mirrored collection last finished a clean sync pass, if any") Instant lastSyncedAt,
    @NonNull
    @Schema(description = "Live (non-tombstoned) mirrored document count across all collections")
    Long documentCount,
    @NonNull
    @Schema(description = "Whether a manually triggered full reconcile is currently running for this workspace")
    Boolean syncRunning,
    @NonNull @Schema(description = "Enabled collections still awaiting a clean sync pass") Long pendingCollections,
    @NonNull
    @Schema(description = "Collections whose last sync attempt recorded an error (cleared on the next clean pass)")
    Long erroredCollections
) {}

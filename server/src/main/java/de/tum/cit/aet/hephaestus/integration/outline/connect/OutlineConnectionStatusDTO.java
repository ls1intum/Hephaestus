package de.tum.cit.aet.hephaestus.integration.outline.connect;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Admin health view of the workspace's ACTIVE Outline connection: whether the change-notification
 * subscription is registered, when a mirrored collection last finished a clean sync pass, and how
 * many live documents the mirror holds. This is the status line the admin connect card renders.
 */
@Schema(description = "Health of the workspace's active Outline connection")
public record OutlineConnectionStatusDTO(
    @NonNull
    @Schema(description = "Whether the Outline change-notification webhook subscription is registered")
    Boolean webhookRegistered,
    @Schema(description = "When a mirrored collection last finished a clean sync pass, if any") Instant lastSyncedAt,
    @NonNull
    @Schema(description = "Live (non-tombstoned) mirrored document count across all collections")
    Long documentCount
) {}

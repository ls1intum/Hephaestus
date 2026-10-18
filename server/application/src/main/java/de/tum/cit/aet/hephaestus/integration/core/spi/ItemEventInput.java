package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One occasion on an item. It carries the exact evidence version, or, when the source cannot pin one yet, no version
 * and a pending fence: an opaque value the source composes from the state it stored at the occasion. The source pins
 * the version later only while its fence is unchanged; core only stores and compares it.
 */
public record ItemEventInput(
        long workspaceId,
        long itemId,
        ItemEventName name,
        @Nullable String itemVersion,
        @Nullable String pendingFence,
        Instant occurredAt,
        FoundBy foundBy,
        @Nullable Long actorUserId,
        @Nullable Long requestedById) {
    public ItemEventInput(
            long workspaceId,
            long itemId,
            ItemEventName name,
            String itemVersion,
            Instant occurredAt,
            FoundBy foundBy,
            @Nullable Long actorUserId,
            @Nullable Long requestedById) {
        this(workspaceId, itemId, name, itemVersion, null, occurredAt, foundBy, actorUserId, requestedById);
    }

    public ItemEventInput {
        if ((foundBy != FoundBy.WEBHOOK && foundBy != FoundBy.SYNC)
                || workspaceId <= 0
                || itemId <= 0
                || (itemVersion == null) == (pendingFence == null)
                || (itemVersion != null && (itemVersion.isBlank() || itemVersion.length() > 1024))
                || (pendingFence != null && (pendingFence.isBlank() || pendingFence.length() > 1024))
                || (actorUserId != null && actorUserId <= 0)
                || (requestedById != null && requestedById <= 0))
            throw new IllegalArgumentException("Invalid item event");
    }
}

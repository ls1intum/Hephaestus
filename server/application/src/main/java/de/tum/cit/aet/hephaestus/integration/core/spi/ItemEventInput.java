package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ItemEventInput(
        long workspaceId,
        long itemId,
        ItemEventName name,
        String itemVersion,
        Instant occurredAt,
        FoundBy foundBy,
        @Nullable Long actorUserId,
        @Nullable Long requestedById) {
    public ItemEventInput {

        if (workspaceId <= 0
                || itemId <= 0
                || itemVersion.isBlank()
                || itemVersion.length() > 1024
                || (actorUserId != null && actorUserId <= 0)
                || (requestedById != null && requestedById <= 0))
            throw new IllegalArgumentException("Invalid item event");
    }
}

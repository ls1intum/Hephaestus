package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * @param eventId the occasion an event review was admitted for; null for every other review
 * @param evidenceExpiresAt when the occasion's retained record and every copy of it may no longer be used; it stays
 *     after the record is removed. Null when there is no occasion or it never kept a record
 */
public record ReviewSnapshot(
        UUID id,
        long workspaceId,
        ItemRef item,
        ReviewKind kind,
        @Nullable ItemVersion version,
        ReviewOrigin origin,
        long rolloutRevision,
        List<PinnedPractice> practices,
        @Nullable UUID eventId,
        @Nullable Instant evidenceExpiresAt) {
    public ReviewSnapshot {
        practices = List.copyOf(practices);
        if (eventId != null && origin != ReviewOrigin.EVENT)
            throw new IllegalArgumentException("Only an event review has an occasion");
        if (evidenceExpiresAt != null && eventId == null)
            throw new IllegalArgumentException("Only an occasion retains evidence");
    }

    public ReviewSnapshot(
            UUID id,
            long workspaceId,
            ItemRef item,
            ReviewKind kind,
            @Nullable ItemVersion version,
            ReviewOrigin origin,
            long rolloutRevision,
            List<PinnedPractice> practices) {
        this(id, workspaceId, item, kind, version, origin, rolloutRevision, practices, null, null);
    }
}

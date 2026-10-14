package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewSnapshot(
        UUID id,
        long workspaceId,
        ItemRef item,
        ReviewKind kind,
        @Nullable ItemVersion version,
        ReviewOrigin origin,
        long rolloutRevision,
        List<PinnedPractice> practices) {
    public ReviewSnapshot {
        practices = List.copyOf(practices);
    }
}

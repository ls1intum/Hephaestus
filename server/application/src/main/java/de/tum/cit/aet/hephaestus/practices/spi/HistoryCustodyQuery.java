package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HistoryCustodyQuery {
    HistoryCustody find(long workspaceId, List<UUID> producingJobIds);

    /**
     * When the producing job of {@code reviewId} captured its evidence, as its retained capture manifest records it;
     * empty when that job, its review or a valid manifest time is missing.
     */
    Optional<Instant> capturedAt(long workspaceId, UUID reviewId, UUID producingJobId);
}

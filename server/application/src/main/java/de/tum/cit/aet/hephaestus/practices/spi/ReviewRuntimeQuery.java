package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReviewRuntimeQuery {
    Optional<ReviewRuntimeSnapshot> runtime(long workspaceId, UUID reviewId);

    Set<UUID> activeJobIds(long workspaceId, UUID reviewId);

    ReviewUsageSnapshot usage(long workspaceId, UUID reviewId);

    ReviewQueueSnapshot queue(long workspaceId, Instant asOf, int headLimit);

    ReviewTimingSnapshot timing(long workspaceId, Instant from, Instant to, Instant longWaitThreshold);
}

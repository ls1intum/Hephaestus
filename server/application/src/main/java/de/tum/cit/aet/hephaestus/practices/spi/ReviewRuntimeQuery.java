package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReviewRuntimeQuery {
    Optional<ReviewRuntimeSnapshot> runtime(long workspaceId, UUID reviewId);

    Map<ReviewJobStatus, Long> jobCounts(long workspaceId, UUID reviewId);

    Set<UUID> activeJobIds(long workspaceId, UUID reviewId);

    Set<UUID> unsettledJobIds(long workspaceId, UUID reviewId);

    ReviewUsageSnapshot usage(long workspaceId, UUID reviewId);

    /**
     * Retained canonical usage for the requested reviews. An absent entry means no ledger rows
     * remain for that review, not measured zero usage. Totals belong to the whole review.
     */
    Map<UUID, ReviewUsageSnapshot> usageByReview(long workspaceId, Set<UUID> reviewIds);

    ReviewQueueSnapshot queue(long workspaceId, Instant asOf, int headLimit);

    ReviewTimingSnapshot timing(long workspaceId, Instant from, Instant to, Instant longWaitThreshold);
}

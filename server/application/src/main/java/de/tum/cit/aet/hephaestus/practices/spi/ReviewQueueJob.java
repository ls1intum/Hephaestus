package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewQueueJob(
        UUID jobId,
        UUID reviewId,
        long itemId,
        @Nullable Long developerId,
        ReviewJobStatus status,
        boolean heldForBudget,
        Instant since) {}

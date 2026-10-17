package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewTimingFact(
        UUID reviewId,
        ReviewOrigin origin,
        Instant createdAt,
        @Nullable Instant eventAt,
        @Nullable Instant completedAt,
        @Nullable Instant publicCompletedAt,
        @Nullable Instant firstAutomaticPostedAt,
        @Nullable Instant firstReadAt,
        @Nullable Instant mergedAt,
        boolean finished,
        boolean startedByMerge) {}

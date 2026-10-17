package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ReviewCompletion(
        ReviewStatus status, @Nullable ReviewStopReason reason, Instant completedAt, Instant orderedAt) {}

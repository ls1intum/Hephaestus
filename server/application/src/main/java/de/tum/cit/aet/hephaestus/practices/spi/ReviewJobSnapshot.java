package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewJobSnapshot(
        UUID id,
        ReviewJobStatus status,
        int attempt,
        ProcessingStatus processingStatus,
        Instant availableAt,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable Integer exitCode,
        @Nullable String errorMessage,
        String traceId) {}

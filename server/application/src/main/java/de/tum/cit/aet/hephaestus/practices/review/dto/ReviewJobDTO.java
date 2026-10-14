package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewJobDTO(
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

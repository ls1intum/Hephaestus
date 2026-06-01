package de.tum.cit.aet.hephaestus.core.auth.export.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Status of a requested data export; timestamps are null until the matching transition occurs. */
public record ExportStatusDTO(
    Long id,
    String status,
    Instant requestedAt,
    @Nullable Instant completedAt,
    @Nullable Instant expiresAt
) {}

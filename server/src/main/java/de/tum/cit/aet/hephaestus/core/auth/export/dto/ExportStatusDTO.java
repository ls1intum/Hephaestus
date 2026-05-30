package de.tum.cit.aet.hephaestus.core.auth.export.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Status view for {@code GET /user/exports/{id}}. {@code status} ∈
 * PENDING/PROCESSING/READY/FAILED/EXPIRED. Timestamps are null until the corresponding lifecycle
 * transition occurs.
 */
public record ExportStatusDTO(
    Long id,
    String status,
    Instant requestedAt,
    @Nullable Instant completedAt,
    @Nullable Instant expiresAt
) {}

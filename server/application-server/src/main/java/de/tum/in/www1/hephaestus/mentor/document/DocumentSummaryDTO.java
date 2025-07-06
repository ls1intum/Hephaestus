package de.tum.in.www1.hephaestus.mentor.document;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for document summary in list views.
 * Excludes content for performance - only metadata.
 */
public record DocumentSummaryDTO(
    @NotNull UUID id,
    @NotNull String title,
    @NotNull DocumentKind kind,
    @NotNull Instant createdAt,
    @NotNull String userId
) {
    /**
     * Convert Document entity to summary DTO (no content)
     */
    public static DocumentSummaryDTO from(Document document) {
        return new DocumentSummaryDTO(
            document.getId(),
            document.getTitle(),
            document.getKind(),
            document.getCreatedAt(),
            document.getUser().getId().toString()
        );
    }
}
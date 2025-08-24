package de.tum.in.www1.hephaestus.mentor.document;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for complete document responses.
 * Used when returning full document details (includes content).
 */
public record DocumentDTO(
    @NotNull UUID id,
    @NotNull Integer versionNumber,
    @NotNull Instant createdAt,
    @NotNull String title,
    @NotNull String content, // Always present in full document view
    @NotNull DocumentKind kind,
    @NotNull String userId
) {
    /**
     * Convert Document entity to complete DTO
     */
    public static DocumentDTO from(Document document) {
        return new DocumentDTO(
            document.getId(),
            document.getVersionNumber(),
            document.getCreatedAt(),
            document.getTitle(),
            document.getContent(),
            document.getKind(),
            document.getUser().getId().toString()
        );
    }
}

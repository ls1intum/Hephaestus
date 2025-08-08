package de.tum.in.www1.hephaestus.mentor.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing document.
 * Creates a new version with the updated content.
 */
public record UpdateDocumentRequestDTO(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,

    @NotNull(message = "Content is required") String content,

    @NotNull(message = "Kind is required") DocumentKind kind
) {
    // Bean Validation handles all validation - no constructor needed
}

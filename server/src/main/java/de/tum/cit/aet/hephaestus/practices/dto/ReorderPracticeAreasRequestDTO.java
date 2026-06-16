package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request to reorder practice areas. Each area's {@code displayOrder} is set to its index in the list,
 * so the whole ordering is written atomically in one request.
 */
@Schema(description = "Reorder practice areas — displayOrder follows the list index")
public record ReorderPracticeAreasRequestDTO(
    @NotEmpty(message = "orderedSlugs must not be empty")
    @Schema(
        description = "Area slugs in the desired display order",
        example = "[\"review-ready-work\", \"acting-on-review-feedback\"]"
    )
    List<String> orderedSlugs
) {}

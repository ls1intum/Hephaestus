package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request to reorder practice goals. Each goal's {@code displayOrder} is set to its index in the list,
 * so the whole ordering is written atomically in one request.
 */
@Schema(description = "Reorder practice goals — displayOrder follows the list index")
public record ReorderPracticeGoalsRequestDTO(
    @NotEmpty(message = "orderedSlugs must not be empty")
    @Schema(
        description = "Goal slugs in the desired display order",
        example = "[\"review-ready-work\", \"acting-on-review-feedback\"]"
    )
    List<String> orderedSlugs
) {}

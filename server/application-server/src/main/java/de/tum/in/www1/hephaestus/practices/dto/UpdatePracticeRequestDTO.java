package de.tum.in.www1.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for updating an existing practice definition.
 *
 * <p>Uses PATCH semantics: only non-null fields are applied.
 */
@Schema(description = "Request to update an existing practice definition (PATCH — only non-null fields applied)")
public record UpdatePracticeRequestDTO(
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    @Schema(description = "Human-readable name", example = "PR Description Quality")
    String name,

    @Size(max = 64, message = "Category must be at most 64 characters")
    @Schema(description = "Practice category", example = "code-quality")
    String category,

    @Size(max = 10000, message = "Description must be at most 10000 characters")
    @Pattern(regexp = ".*\\S.*", message = "Description must not be blank")
    @Schema(description = "Practice description")
    String description,

    @Size(min = 1, max = 10, message = "Trigger events must contain between 1 and 10 entries")
    @ValidTriggerEvents
    @Schema(description = "Domain events that trigger detection")
    List<String> triggerEvents,

    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @Schema(description = "Practice evaluation criteria")
    String criteria
) {}

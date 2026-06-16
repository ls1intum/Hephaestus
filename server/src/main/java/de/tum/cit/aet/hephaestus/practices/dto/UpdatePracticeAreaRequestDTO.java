package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for updating an existing practice goal.
 *
 * <p>Uses PATCH semantics: only non-null fields are applied.
 */
@Schema(description = "Request to update an existing practice goal (PATCH — only non-null fields applied)")
public record UpdatePracticeAreaRequestDTO(
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    @Schema(description = "Human-readable name")
    @Nullable
    String name,

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    @Schema(description = "What this goal develops")
    @Nullable
    String description,

    @Schema(description = "Sort order within the workspace") @Nullable Integer displayOrder,

    @Schema(description = "Whether this goal is active") @Nullable Boolean active
) {}

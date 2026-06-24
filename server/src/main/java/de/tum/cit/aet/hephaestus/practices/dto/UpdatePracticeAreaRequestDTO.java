package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for updating an existing practice area.
 *
 * <p>Uses PATCH semantics: only non-null fields are applied.
 */
@Schema(description = "Request to update an existing practice area (PATCH — only non-null fields applied)")
public record UpdatePracticeAreaRequestDTO(
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    @Schema(description = "Human-readable name")
    @Nullable
    String name,

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    @Schema(description = "What this area develops")
    @Nullable
    String description,

    @Schema(description = "Sort order within the workspace") @Nullable Integer displayOrder,

    @Schema(description = "Whether this area is active") @Nullable Boolean active,

    @Size(max = 64, message = "Icon must be at most 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Icon must be a lucide icon name (letters and digits only)")
    @Schema(description = "Optional lucide icon name giving the area a glanceable identity")
    @Nullable
    String icon,

    @Size(max = 32, message = "Color must be at most 32 characters")
    @Pattern(regexp = "^[a-z]+$", message = "Color must be a lowercase palette key")
    @Schema(description = "Optional palette colour key for the area's chip")
    @Nullable
    String color
) {}

package de.tum.in.www1.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a new practice definition.
 */
@Schema(description = "Request to create a new practice definition")
public record CreatePracticeRequestDTO(
    @NotBlank(message = "Slug is required")
    @Size(min = 3, max = 64, message = "Slug must be between 3 and 64 characters")
    @Pattern(
        regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
        message = "Slug must contain only lowercase alphanumeric characters and hyphens," +
            " must not start or end with a hyphen, and must not contain consecutive hyphens"
    )
    @Schema(
        description = "URL-safe identifier unique within the workspace",
        example = "pr-description-quality",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String slug,

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @Schema(
        description = "Human-readable name",
        example = "PR Description Quality",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String name,

    @Size(max = 64, message = "Category must be at most 64 characters")
    @Schema(description = "Practice category", example = "code-quality")
    String category,

    @NotBlank(message = "Description is required")
    @Size(max = 10000, message = "Description must be at most 10000 characters")
    @Schema(
        description = "Practice description",
        example = "Ensures pull request descriptions are detailed and informative",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String description,

    @NotNull(message = "Trigger events are required")
    @Size(min = 1, max = 10, message = "Trigger events must contain between 1 and 10 entries")
    @ValidTriggerEvents
    @Schema(
        description = "Domain events that trigger detection",
        example = "[\"PullRequestCreated\", \"ReviewSubmitted\"]",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<String> triggerEvents,

    @Size(max = 50000, message = "Detection prompt must be at most 50000 characters")
    @Schema(description = "AI detection prompt template")
    String detectionPrompt
) {}

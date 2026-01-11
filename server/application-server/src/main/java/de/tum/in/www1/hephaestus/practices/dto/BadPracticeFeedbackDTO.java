package de.tum.in.www1.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for submitting feedback on a detected bad practice.
 *
 * @param type        the type of feedback (alphanumeric, max 64 chars)
 * @param explanation the user's explanation (max 2000 chars)
 */
@Schema(description = "User feedback on a detected bad practice")
public record BadPracticeFeedbackDTO(
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Type must be alphanumeric")
    @Schema(
        description = "Type of feedback (e.g., 'false_positive', 'not_applicable')",
        example = "false_positive",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String type,
    @NotBlank
    @Size(max = 2000)
    @Schema(
        description = "User's explanation for the feedback",
        maxLength = 2000,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String explanation
) {}

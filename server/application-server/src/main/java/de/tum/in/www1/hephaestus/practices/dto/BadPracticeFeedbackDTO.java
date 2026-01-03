package de.tum.in.www1.hephaestus.practices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for submitting feedback on a detected bad practice.
 *
 * @param type        the type of feedback (alphanumeric, max 64 chars)
 * @param explanation the user's explanation (max 2000 chars)
 */
public record BadPracticeFeedbackDTO(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Type must be alphanumeric") String type,
    @NotBlank @Size(max = 2000) String explanation
) {}

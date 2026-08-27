package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Request to create a new practice group")
public record CreatePracticeGroupRequestDTO(
        @NotBlank(message = "Slug is required")
        @Size(min = 3, max = 64, message = "Slug must be between 3 and 64 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug must contain only lowercase alphanumeric characters and hyphens,"
                        + " must not start or end with a hyphen, and must not contain consecutive hyphens")
        @Schema(description = "URL-safe identifier unique within the workspace", example = "review-ready-work")
        String slug,

        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
        @Schema(description = "Human-readable name", example = "Submitting review-ready work")
        String name,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        @Schema(description = "What this group develops")
        @Nullable
        String description,

        @PositiveOrZero(message = "Display order must be zero or positive")
        @Schema(description = "Sort order within the workspace. Appended when omitted.", example = "1")
        @Nullable
        Integer displayOrder,

        @Size(max = 64, message = "Icon must be at most 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Icon must be a lucide icon name (letters and digits only)")
        @Schema(
                description = "Optional lucide icon name giving the group a glanceable identity",
                example = "ShieldAlert")
        @Nullable
        String icon,

        @Size(max = 32, message = "Color must be at most 32 characters")
        @Pattern(regexp = "^[a-z]+$", message = "Color must be a lowercase palette key")
        @Schema(description = "Optional palette colour key for the group's chip", example = "rose")
        @Nullable
        String color) {}

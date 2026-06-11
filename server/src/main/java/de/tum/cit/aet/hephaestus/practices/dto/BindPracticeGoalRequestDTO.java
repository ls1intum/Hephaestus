package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for binding a practice to a goal (or unbinding it when {@code goalSlug} is null).
 */
@Schema(description = "Request to bind a practice to a goal, or unbind it when goalSlug is null")
public record BindPracticeGoalRequestDTO(
    @Schema(description = "Slug of the goal to bind to, or null to unbind", example = "review-ready-work")
    @Nullable
    String goalSlug
) {}

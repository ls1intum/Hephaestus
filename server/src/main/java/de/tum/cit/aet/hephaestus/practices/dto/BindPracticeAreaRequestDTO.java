package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Request DTO for binding a practice to an area (or unbinding it when {@code areaSlug} is null).
 */
@Schema(description = "Request to bind a practice to an area, or unbind it when areaSlug is null")
public record BindPracticeAreaRequestDTO(
    @Schema(description = "Slug of the area to bind to, or null to unbind", example = "review-ready-work")
    @Nullable
    String areaSlug
) {}

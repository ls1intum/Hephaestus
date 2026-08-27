package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A practice's position in the catalog")
public record PlacePracticeRequestDTO(
    @Schema(
        description = "Destination group slug; omit to place the practice in Unassigned",
        example = "review-ready-work"
    )
    @Nullable
    String groupSlug,

    @JsonProperty(required = true)
    @NonNull
    @NotNull(message = "position is required")
    @Min(value = 0, message = "position must be zero or greater")
    @Schema(description = "Zero-based position in the destination", example = "1")
    Integer position
) {}

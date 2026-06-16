package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for a practice area — a configurable grouping of related practices.
 */
@Schema(description = "A practice area grouping related practices into a learning objective")
public record PracticeAreaDTO(
    @NonNull @Schema(description = "Area ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within the workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "What this area develops") String description,
    @NonNull @Schema(description = "Whether this area is active") Boolean active,
    @NonNull @Schema(description = "Sort order within the workspace") Integer displayOrder,
    @NonNull @Schema(description = "Timestamp when the area was created") Instant createdAt,
    @Nullable @Schema(description = "Timestamp when the area was last updated") Instant updatedAt
) {
    public static PracticeAreaDTO from(PracticeArea area) {
        return new PracticeAreaDTO(
            area.getId(),
            area.getSlug(),
            area.getName(),
            area.getDescription(),
            area.isActive(),
            area.getDisplayOrder(),
            area.getCreatedAt(),
            area.getUpdatedAt()
        );
    }
}

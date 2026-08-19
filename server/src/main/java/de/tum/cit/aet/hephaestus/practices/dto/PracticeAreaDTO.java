package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
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
    @Nullable @Schema(description = "Optional lucide icon name for the area's chip") String icon,
    @Nullable @Schema(description = "Optional palette colour key for the area's chip") String color,
    @NonNull
    @Schema(description = "Whether this area is shown in practice dashboards")
    Boolean visibleInPracticeDashboards,
    @NonNull @Schema(description = "Sort order within the workspace") Integer displayOrder,
    @NonNull
    @Schema(
        description = "How much autonomy the system has over every practice in this area that holds no " +
            "autonomy of its own, whether that was set here or inherited from the workspace, and which level " +
            "decided it"
    )
    AutonomyAssignmentDTO autonomy,
    @NonNull @Schema(description = "Timestamp when the area was created") Instant createdAt,
    @Nullable @Schema(description = "Timestamp when the area was last updated") Instant updatedAt,
    @Nullable CatalogOriginDTO catalogOrigin
) {
    /** @param workspaceDefault the workspace's effective default autonomy, the bottom of the inheritance chain */
    public static PracticeAreaDTO from(
        PracticeArea area,
        @Nullable CatalogOriginDTO catalogOrigin,
        PracticeAutonomy workspaceDefault
    ) {
        return new PracticeAreaDTO(
            area.getId(),
            area.getSlug(),
            area.getName(),
            area.getDescription(),
            area.getIcon(),
            area.getColor(),
            area.isVisibleInPracticeDashboards(),
            area.getDisplayOrder(),
            AutonomyAssignmentDTO.of(AutonomyResolver.resolveArea(area, workspaceDefault), area.getAutonomy()),
            area.getCreatedAt(),
            area.getUpdatedAt(),
            catalogOrigin
        );
    }
}

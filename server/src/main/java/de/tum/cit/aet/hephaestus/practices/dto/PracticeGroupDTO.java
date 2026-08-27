package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for a practice group — a configurable grouping of related practices.
 */
@Schema(description = "A practice group grouping related practices into a learning objective")
public record PracticeGroupDTO(
    @NonNull @Schema(description = "Group ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within the workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "What this group develops") String description,
    @Nullable @Schema(description = "Optional lucide icon name for the group's chip") String icon,
    @Nullable @Schema(description = "Optional palette colour key for the group's chip") String color,
    @NonNull
    @Schema(description = "Whether this group is shown in practice dashboards")
    Boolean visibleInPracticeDashboards,
    @NonNull @Schema(description = "Sort order within the workspace") Integer displayOrder,
    @NonNull
    @Schema(
        description = "How much autonomy the system has over every practice in this group that holds no " +
            "autonomy of its own, whether that was set here or inherited from the workspace, and which level " +
            "decided it"
    )
    AutonomyAssignmentDTO autonomy,
    @NonNull @Schema(description = "Timestamp when the group was created") Instant createdAt,
    @Nullable @Schema(description = "Timestamp when the group was last updated") Instant updatedAt,
    @Nullable CatalogOriginDTO catalogOrigin
) {
    /** @param workspaceDefault the workspace's effective default autonomy, the bottom of the inheritance chain */
    public static PracticeGroupDTO from(
        PracticeGroup group,
        @Nullable CatalogOriginDTO catalogOrigin,
        PracticeAutonomy workspaceDefault
    ) {
        return new PracticeGroupDTO(
            group.getId(),
            group.getSlug(),
            group.getName(),
            group.getDescription(),
            group.getIcon(),
            group.getColor(),
            group.isVisibleInPracticeDashboards(),
            group.getDisplayOrder(),
            AutonomyAssignmentDTO.of(AutonomyResolver.resolveGroup(group, workspaceDefault), group.getAutonomy()),
            group.getCreatedAt(),
            group.getUpdatedAt(),
            catalogOrigin
        );
    }
}

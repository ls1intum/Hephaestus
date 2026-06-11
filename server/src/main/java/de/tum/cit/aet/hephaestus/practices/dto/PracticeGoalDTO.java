package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for a practice goal — a configurable grouping of related practices.
 */
@Schema(description = "A practice goal grouping related practices into a learning objective")
public record PracticeGoalDTO(
    @NonNull @Schema(description = "Goal ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within the workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "What this goal develops") String description,
    @NonNull @Schema(description = "Whether this goal is active") Boolean active,
    @NonNull @Schema(description = "Sort order within the workspace") Integer displayOrder,
    @NonNull @Schema(description = "Timestamp when the goal was created") Instant createdAt,
    @Nullable @Schema(description = "Timestamp when the goal was last updated") Instant updatedAt
) {
    public static PracticeGoalDTO from(PracticeGoal goal) {
        return new PracticeGoalDTO(
            goal.getId(),
            goal.getSlug(),
            goal.getName(),
            goal.getDescription(),
            goal.isActive(),
            goal.getDisplayOrder(),
            goal.getCreatedAt(),
            goal.getUpdatedAt()
        );
    }
}

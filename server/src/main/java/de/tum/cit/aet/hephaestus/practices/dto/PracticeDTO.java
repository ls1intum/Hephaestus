package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for practice definitions.
 */
@Schema(description = "Practice definition for evaluating developer contributions")
public record PracticeDTO(
    @NonNull @Schema(description = "Practice ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "Practice category") String category,
    @NonNull @Schema(description = "Domain events that trigger detection") List<String> triggerEvents,
    @NonNull @Schema(description = "Practice evaluation criteria") String criteria,
    @Nullable
    @Schema(description = "TypeScript/Bun precompute script for static analysis before AI review")
    String precomputeScript,
    @NonNull @Schema(description = "Whether this practice is actively being detected") Boolean active,
    @NonNull @Schema(description = "Timestamp when the practice was created") Instant createdAt,
    @NonNull @Schema(description = "Timestamp when the practice was last updated") Instant updatedAt
) {
    public static PracticeDTO from(Practice practice) {
        return new PracticeDTO(
            practice.getId(),
            practice.getSlug(),
            practice.getName(),
            practice.getCategory(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()),
            practice.getCriteria(),
            practice.getPrecomputeScript(),
            practice.isActive(),
            practice.getCreatedAt(),
            practice.getUpdatedAt()
        );
    }
}

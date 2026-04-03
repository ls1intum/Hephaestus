package de.tum.in.www1.hephaestus.practices.dto;

import de.tum.in.www1.hephaestus.practices.model.Practice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Response DTO for practice definitions.
 */
@Schema(description = "Practice definition for evaluating developer contributions")
public record PracticeDTO(
    @NonNull @Schema(description = "Practice ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "Practice category") String category,
    @NonNull @Schema(description = "Practice description") String description,
    @NonNull @Schema(description = "Domain events that trigger detection") List<String> triggerEvents,
    @Nullable @Schema(description = "Practice evaluation criteria") String criteria,
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
            practice.getDescription(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()),
            practice.getCriteria(),
            practice.isActive(),
            practice.getCreatedAt(),
            practice.getUpdatedAt()
        );
    }
}

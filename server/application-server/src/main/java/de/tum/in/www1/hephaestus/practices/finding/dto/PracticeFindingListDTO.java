package de.tum.in.www1.hephaestus.practices.finding.dto;

import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * List-view DTO for practice findings. Omits large text fields (guidance, reasoning)
 * and internal fields (agentJobId, idempotencyKey, evidence) to keep payloads small.
 */
@Schema(description = "Practice finding summary for list views")
public record PracticeFindingListDTO(
    @NonNull @Schema(description = "Finding ID") UUID id,
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @Nullable @Schema(description = "Practice category") String category,
    @NonNull @Schema(description = "Target type (e.g. PULL_REQUEST)") PracticeFindingTargetType targetType,
    @NonNull @Schema(description = "Target entity ID") Long targetId,
    @NonNull @Schema(description = "Finding title") String title,
    @NonNull @Schema(description = "Verdict: POSITIVE, NEGATIVE, NOT_APPLICABLE, or NEEDS_REVIEW") Verdict verdict,
    @NonNull @Schema(description = "Severity level") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @Nullable @Schema(description = "Cognitive apprenticeship guidance method") CaMethod guidanceMethod,
    @NonNull @Schema(description = "When the finding was detected") Instant detectedAt
) {
    /**
     * Maps a {@link PracticeFinding} entity (with eagerly fetched practice) to a list DTO.
     */
    public static PracticeFindingListDTO from(PracticeFinding f) {
        var practice = f.getPractice();
        return new PracticeFindingListDTO(
            f.getId(),
            practice.getSlug(),
            practice.getName(),
            practice.getCategory(),
            f.getTargetType(),
            f.getTargetId(),
            f.getTitle(),
            f.getVerdict(),
            f.getSeverity(),
            f.getConfidence(),
            f.getGuidanceMethod(),
            f.getDetectedAt()
        );
    }
}

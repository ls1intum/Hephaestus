package de.tum.in.www1.hephaestus.practices.finding.dto;

import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Detail-view DTO for a single practice finding. Includes guidance, reasoning,
 * and structured evidence that are omitted from the list view.
 *
 * <p>Intentionally omits internal fields: {@code agentJobId}, {@code idempotencyKey},
 * and raw {@code contributorId}.
 */
@Schema(description = "Full practice finding detail including guidance and evidence")
public record PracticeFindingDetailDTO(
    @NonNull @Schema(description = "Finding ID") UUID id,
    @NonNull @Schema(description = "Practice slug") String practiceSlug,
    @NonNull @Schema(description = "Practice name") String practiceName,
    @Nullable @Schema(description = "Practice category") String category,
    @NonNull @Schema(description = "Target type (e.g. PULL_REQUEST)") String targetType,
    @NonNull @Schema(description = "Target entity ID") Long targetId,
    @NonNull @Schema(description = "Finding title") String title,
    @NonNull @Schema(description = "Verdict: POSITIVE, NEGATIVE, NOT_APPLICABLE, or NEEDS_REVIEW") Verdict verdict,
    @NonNull @Schema(description = "Severity level") Severity severity,
    @NonNull @Schema(description = "AI confidence score (0.0–1.0)") Float confidence,
    @Nullable @Schema(description = "Structured evidence (locations, snippets, references)") Object evidence,
    @Nullable @Schema(description = "AI reasoning behind the verdict") String reasoning,
    @Nullable @Schema(description = "Actionable guidance for the contributor") String guidance,
    @Nullable @Schema(description = "Cognitive apprenticeship guidance method") CaMethod guidanceMethod,
    @NonNull @Schema(description = "When the finding was detected") Instant detectedAt
) {
    /**
     * Maps a {@link PracticeFinding} entity (with eagerly fetched practice) to a detail DTO.
     */
    public static PracticeFindingDetailDTO from(PracticeFinding f) {
        var practice = f.getPractice();
        return new PracticeFindingDetailDTO(
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
            f.getEvidence(),
            f.getReasoning(),
            f.getGuidance(),
            f.getGuidanceMethod(),
            f.getDetectedAt()
        );
    }
}

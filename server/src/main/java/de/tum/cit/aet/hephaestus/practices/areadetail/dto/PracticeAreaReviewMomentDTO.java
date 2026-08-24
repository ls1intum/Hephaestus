package de.tum.cit.aet.hephaestus.practices.areadetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A complete review run in a learner's practice-area history")
public record PracticeAreaReviewMomentDTO(
    @NonNull UUID reviewId,
    @NonNull Instant reviewedAt,
    @NonNull PracticeAreaReviewArtifactDTO artifact,
    @NonNull List<PracticeAreaReviewFindingDTO> findings
) {}

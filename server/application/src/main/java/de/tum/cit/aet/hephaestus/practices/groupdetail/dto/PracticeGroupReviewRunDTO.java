package de.tum.cit.aet.hephaestus.practices.groupdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A complete review run in a developer's practice-group history")
public record PracticeGroupReviewRunDTO(
        @NonNull UUID reviewId,
        @NonNull Instant reviewedAt,
        @NonNull PracticeGroupReviewedWorkDTO reviewedWork,
        @NonNull List<PracticeGroupReviewObservationDTO> observations) {}

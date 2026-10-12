package de.tum.cit.aet.hephaestus.practices.groupdetail.dto;

import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A complete review run in a developer's practice-group history")
public record PracticeGroupReviewRunDTO(
        @NonNull UUID reviewId,
        @NonNull Instant reviewedAt,
        @NonNull ReviewedWorkRefDTO reviewedWork,

        @NonNull @Schema(description = "Every visible result of the run, complete enough to open in place")
        List<PracticeResultDetailDTO> practiceResults) {}

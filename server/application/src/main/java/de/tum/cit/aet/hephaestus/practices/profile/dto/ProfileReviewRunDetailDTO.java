package de.tum.cit.aet.hephaestus.practices.profile.dto;

import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultDetailDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "One review run on the developer's own work and what it observed about them")
public record ProfileReviewRunDetailDTO(
        @NonNull ProfileReviewRunDTO run,

        @NonNull @Schema(description = "Every visible result the run made about this developer")
        List<PracticeResultDetailDTO> practiceResults) {}

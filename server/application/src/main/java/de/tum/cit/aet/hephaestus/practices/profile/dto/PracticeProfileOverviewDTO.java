package de.tum.cit.aet.hephaestus.practices.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.GoingWellPracticeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeProfileOverviewDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OverviewWindowDTO window,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ProfileReviewRefDTO latestReview,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GoingWellPracticeDTO> goingWell,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProfileChangeDTO> changes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ItemRefDTO> reviewedItems) {
    public PracticeProfileOverviewDTO {
        goingWell = List.copyOf(goingWell);
        changes = List.copyOf(changes);
        reviewedItems = List.copyOf(reviewedItems);
    }
}

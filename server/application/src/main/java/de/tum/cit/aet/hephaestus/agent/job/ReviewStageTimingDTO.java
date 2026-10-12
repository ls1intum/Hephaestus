package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "How long one stage of practice reviews took, over the reviews that passed through it")
public record ReviewStageTimingDTO(
        @NonNull @Schema(description = "Reviews that passed through the stage and were measured")
        Long count,

        @Schema(description = "The median duration in whole seconds; absent when no review was measured") @Nullable
        Long medianSeconds,

        @Schema(description = "The 90th percentile duration in whole seconds; absent when no review was measured")
        @Nullable
        Long p90Seconds) {

    static final ReviewStageTimingDTO EMPTY = new ReviewStageTimingDTO(0L, null, null);
}

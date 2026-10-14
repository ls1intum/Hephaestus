package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.trend.OutcomeVector;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OutcomeVectorDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int met,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int notMet,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int doesNotApply,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int couldNotDetermine) {
    public static @Nullable OutcomeVectorDTO from(@Nullable OutcomeVector v) {
        return v == null ? null : new OutcomeVectorDTO(v.met(), v.notMet(), v.doesNotApply(), v.couldNotDetermine());
    }
}

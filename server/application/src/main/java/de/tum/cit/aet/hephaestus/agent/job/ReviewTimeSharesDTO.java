package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(
        description = "Each stage's part of the time of a set of whole reviews, from 0 to 1: its seconds summed over"
                + " the set, divided by the reviews' summed seconds. The parts add up to 1")
public record ReviewTimeSharesDTO(
        @NonNull
        @Schema(
                description = "Until asked: from the moment on the work that started the review to its request; 0"
                        + " where no moment started it")
        Double asked,

        @NonNull @Schema(description = "Waiting in the queue")
        Double queue,

        @NonNull @Schema(description = "Preparing the evidence")
        Double preparation,

        @NonNull @Schema(description = "With the model") Double model,

        @NonNull
        @Schema(
                description = "The whole run of the reviews without a recorded execution start, such as those"
                        + " recorded before it was, so it cannot be split into preparation and model; 0 when every"
                        + " review has one")
        Double runUnsplit,

        @Schema(description = "Until the comment was posted; absent for reviews that are not measured to a comment")
        @Nullable
        Double posted) {

    /** The parts of these stage sums, or null when the set is empty or took no time at all. */
    static @Nullable ReviewTimeSharesDTO of(
            @Nullable Double asked,
            @Nullable Double queue,
            @Nullable Double preparation,
            @Nullable Double model,
            @Nullable Double runUnsplit,
            @Nullable Double posted) {
        if (asked == null || queue == null || preparation == null || model == null || runUnsplit == null) {
            return null;
        }
        double total = asked + queue + preparation + model + runUnsplit + (posted == null ? 0 : posted);
        if (total <= 0) {
            return null;
        }
        return new ReviewTimeSharesDTO(
                asked / total,
                queue / total,
                preparation / total,
                model / total,
                runUnsplit / total,
                posted == null ? null : posted / total);
    }
}

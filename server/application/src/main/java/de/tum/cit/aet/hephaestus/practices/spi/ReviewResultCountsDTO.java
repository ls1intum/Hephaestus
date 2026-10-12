package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.OutcomeCounts;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Counts of results by assessment")
public record ReviewResultCountsDTO(
        @NonNull Long met,
        @NonNull Long notMet,

        @NonNull @Schema(description = "Practices whose subject did not occur in this work")
        Long notApplicable,

        @NonNull
        @Schema(
                description = "Practices that looked at the evidence and could not settle the question either way; "
                        + "reported apart from notApplicable because one says there was nothing here to judge and the "
                        + "other says we could not tell")
        Long undetermined) {
    public static ReviewResultCountsDTO empty() {
        return new ReviewResultCountsDTO(0L, 0L, 0L, 0L);
    }

    public static ReviewResultCountsDTO from(@Nullable OutcomeCounts counts) {
        return counts == null
                ? empty()
                : new ReviewResultCountsDTO(
                        counts.getMet(), counts.getNotMet(), counts.getNotApplicable(), counts.getUndetermined());
    }

    public ReviewResultCountsDTO plus(ReviewResultCountsDTO other) {
        return new ReviewResultCountsDTO(
                met + other.met,
                notMet + other.notMet,
                notApplicable + other.notApplicable,
                undetermined + other.undetermined);
    }
}

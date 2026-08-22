package de.tum.cit.aet.hephaestus.practices.reviewhistory.dto;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One concrete, evidence-backed result inside a review moment")
public record PracticeAreaReviewFindingDTO(
    @NonNull UUID observationId,
    @Nullable UUID feedbackId,
    @Nullable Boolean helpful,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @NonNull String title,
    @NonNull Presence presence,
    @NonNull Assessment assessment,
    @Nullable Severity severity,
    @Nullable String recurrenceKey
) {
    public static PracticeAreaReviewFindingDTO from(
        Observation observation,
        @Nullable UUID feedbackId,
        @Nullable Boolean helpful
    ) {
        return new PracticeAreaReviewFindingDTO(
            observation.getId(),
            feedbackId,
            helpful,
            observation.getPractice().getSlug(),
            observation.getPractice().getName(),
            observation.getSummary(),
            observation.getPresence(),
            observation.getAssessment(),
            observation.getSeverity(),
            observation.getRecurrenceKey()
        );
    }
}

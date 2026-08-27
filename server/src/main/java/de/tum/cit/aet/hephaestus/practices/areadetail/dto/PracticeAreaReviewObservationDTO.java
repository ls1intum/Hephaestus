package de.tum.cit.aet.hephaestus.practices.areadetail.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One concrete, evidence-backed observation from a review run")
public record PracticeAreaReviewObservationDTO(
    @NonNull UUID observationId,
    @Nullable UUID feedbackId,
    @Nullable FeedbackUsefulness feedbackUsefulness,
    @Nullable FeedbackResolution feedbackResolution,
    @Nullable String feedbackResponseComment,
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @NonNull String title,
    @NonNull Presence presence,
    @Nullable
    @Schema(description = "Good or bad for the developer; null when the review could not decide (INCONCLUSIVE)")
    Assessment assessment,
    @Nullable Severity severity,
    @Nullable String recurrenceKey
) {
    public static PracticeAreaReviewObservationDTO from(
        Observation observation,
        @Nullable UUID feedbackId,
        @Nullable FeedbackUsefulness feedbackUsefulness,
        @Nullable FeedbackResolution feedbackResolution,
        @Nullable String feedbackResponseComment
    ) {
        return new PracticeAreaReviewObservationDTO(
            observation.getId(),
            feedbackId,
            feedbackUsefulness,
            feedbackResolution,
            feedbackResponseComment,
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

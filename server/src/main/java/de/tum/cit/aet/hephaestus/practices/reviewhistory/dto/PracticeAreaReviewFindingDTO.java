package de.tum.cit.aet.hephaestus.practices.reviewhistory.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRatingState;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One concrete, evidence-backed result inside a review moment.
 *
 * @param assessment good or bad for the developer, and null exactly when {@code presence} is
 *     {@code INCONCLUSIVE} — the practice looked, the evidence it needed was there, and it still did not
 *     settle the question either way. This history is the inspectable record of what a review saw rather than
 *     the learner's reflection surface, so an undecided finding belongs in it: dropping it would make a
 *     practice that ran and hedged indistinguishable from one that never ran. Render it as undecided rather
 *     than as a verdict — {@code presence} carries the fact, and there is no valence to show.
 */
@Schema(description = "One concrete, evidence-backed result inside a review moment")
public record PracticeAreaReviewFindingDTO(
    @NonNull UUID observationId,
    @Nullable UUID feedbackId,
    @Nullable FeedbackRatingState rating,
    @Nullable String ratingComment,
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
    public static PracticeAreaReviewFindingDTO from(
        Observation observation,
        @Nullable UUID feedbackId,
        @Nullable FeedbackRatingState rating,
        @Nullable String ratingComment
    ) {
        return new PracticeAreaReviewFindingDTO(
            observation.getId(),
            feedbackId,
            rating,
            ratingComment,
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

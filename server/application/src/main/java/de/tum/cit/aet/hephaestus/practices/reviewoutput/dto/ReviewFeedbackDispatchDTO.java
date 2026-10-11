package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One delivery of a package to the provider: the summary and the line comments it carries, tried until it was sent,
 * held back or given up. Leases, write markers and the package content stay out.
 */
@Schema(description = "One delivery of feedback to the provider and how its attempts went")
public record ReviewFeedbackDispatchDTO(
        @NonNull UUID id,

        @NonNull
        @Schema(
                description = "AUTOMATIC_REVIEW_PACKAGE is the review's own package of every piece of feedback it"
                        + " posted without approval; APPROVED_REVIEW_PACKAGE is this approved proposal's package")
        FeedbackDispatchDestination destination,

        @NonNull FeedbackDispatchState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "How many times it was tried")
        int attemptCount,

        @Schema(description = "When it is tried next; null once it was sent, held back or given up") @Nullable
        Instant nextAttemptAt,

        @Schema(description = "The error of the last failed attempt, as the provider or the server reported it")
        @Nullable
        String lastError,

        @Schema(description = "Why it was held back; null unless the state is SUPPRESSED") @Nullable
        FeedbackSuppressionReason suppressionReason,

        @Schema(description = "Where the delivered summary can be opened on the provider") @Nullable
        String deliveredExternalUrl,

        @Schema(description = "The provider's id of the delivered summary") @Nullable
        String deliveredExternalRef,

        @NonNull Instant createdAt,
        @NonNull Instant updatedAt) {

    private static final Set<FeedbackDispatchState> OPEN =
            EnumSet.of(FeedbackDispatchState.PENDING, FeedbackDispatchState.CLAIMED, FeedbackDispatchState.UNCERTAIN);

    public static ReviewFeedbackDispatchDTO from(FeedbackDispatch dispatch) {
        return new ReviewFeedbackDispatchDTO(
                dispatch.getId(),
                dispatch.getDestination(),
                dispatch.getState(),
                dispatch.getAttemptCount(),
                OPEN.contains(dispatch.getState()) ? dispatch.getNextAttemptAt() : null,
                dispatch.getLastError(),
                StoredEnums.parse(dispatch.getSuppressionReason(), FeedbackSuppressionReason.class),
                dispatch.getDeliveredExternalUrl(),
                dispatch.getDeliveredExternalRef(),
                dispatch.getCreatedAt(),
                dispatch.getUpdatedAt());
    }
}

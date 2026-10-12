package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A piece of feedback in a lineage, with where it went and how the developer answered")
public record ReviewLineageFeedbackDTO(
        @NonNull UUID id,

        @NonNull @Schema(description = "The review that composed it")
        UUID agentJobId,

        @NonNull FeedbackChannel channel,
        @NonNull FeedbackDeliveryState deliveryState,

        @Schema(description = "Why delivery stopped; set on withheld or terminally partial feedback") @Nullable
        FeedbackSuppressionReason suppressionReason,

        @NonNull Instant createdAt,

        @Schema(description = "When it was placed; null if it was not delivered") @Nullable
        Instant deliveredAt,

        @Schema(
                description = "How many comments, notes or conversation turns placed it",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int placementCount,

        @NonNull @Schema(description = "Every result it was written from, in render order")
        List<UUID> practiceResultIds,

        @Schema(description = "The earlier feedback it replaced; null on a first delivery") @Nullable
        UUID replacesId,

        @Schema(description = "The newer feedback that replaced it; null while nothing did") @Nullable
        UUID replacedById,

        @Schema(description = "When the newer feedback that replaced it was written; null while nothing did") @Nullable
        Instant replacedAt,

        @Schema(
                description = "Whether the request names this feedback, or the result it names led to it",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean focus,

        @Schema(description = "How the developer answered it; null while nothing stands") @Nullable
        ReviewLineageResponseDTO response,

        @NonNull
        @Schema(
                description = "Where it appears, by the provider that shows it: the channel split where the provider"
                        + " is known, as the feedback list names it")
        FeedbackLocation location,

        @Schema(description = "What an admin decided about it before delivery; null while nobody decided") @Nullable
        FeedbackApprovalDecision decision,

        @Schema(description = "When an admin decided about it; null while nobody decided") @Nullable
        Instant decidedAt,

        @Schema(
                description = "When an admin withdrew it from the developer's practice pages; null unless a withdrawal"
                        + " is in force")
        @Nullable
        Instant withdrawnAt) {}

package de.tum.cit.aet.hephaestus.practices.feedback.approval.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApproval;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackRejectionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedbackApprovalDTO(
        UUID feedbackId,
        FeedbackApprovalDecision decision,
        FeedbackRejectionReason rejectionReason,
        @Nullable String rejectionNote,
        @Nullable Long actorAccountId,

        @Schema(description = "Display name of the admin who decided; null when not resolved or the account is erased")
        @Nullable
        String decidedBy,

        Instant decidedAt) {
    public static FeedbackApprovalDTO from(FeedbackApproval approval, @Nullable String decidedBy) {
        return new FeedbackApprovalDTO(
                approval.getFeedbackId(),
                approval.getDecision(),
                approval.getRejectionReason(),
                approval.getRejectionNote(),
                approval.getActorAccountId(),
                decidedBy,
                approval.getDecidedAt());
    }
}

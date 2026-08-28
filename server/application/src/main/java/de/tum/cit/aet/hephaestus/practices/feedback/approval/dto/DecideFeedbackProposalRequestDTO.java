package de.tum.cit.aet.hephaestus.practices.feedback.approval.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record DecideFeedbackProposalRequestDTO(
        @NotNull FeedbackApprovalDecision decision,
        @Nullable FeedbackRejectionReason rejectionReason,
        @Size(max = 500) @Nullable String rejectionNote) {}

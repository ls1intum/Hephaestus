package de.tum.cit.aet.hephaestus.practices.feedback.approval.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackRejectionReason;
import jakarta.validation.constraints.NotNull;

public record DecideFeedbackProposalRequestDTO(
    @NotNull FeedbackApprovalDecision decision,
    FeedbackRejectionReason rejectionReason
) {}

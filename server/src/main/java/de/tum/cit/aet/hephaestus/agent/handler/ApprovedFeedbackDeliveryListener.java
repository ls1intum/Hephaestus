package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.ApprovedFeedbackReadyEvent;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalEligibility;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class ApprovedFeedbackDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovedFeedbackDeliveryListener.class);
    private final FeedbackRepository feedbackRepository;
    private final FeedbackApprovalRepository approvalRepository;
    private final AgentJobRepository agentJobRepository;
    private final PracticeFeedbackDeliveryPolicy deliveryPolicy;
    private final PracticeFeedbackDispatchService dispatchService;
    private final FeedbackApprovalEligibility approvalEligibility;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ApprovedFeedbackReadyEvent event) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(event.feedbackId(), event.workspaceId())
            .orElse(null);
        if (feedback == null || feedback.getDeliveryState() != FeedbackDeliveryState.PREPARED) return;
        var approval = approvalRepository
            .findByFeedbackIdAndWorkspaceId(feedback.getId(), event.workspaceId())
            .orElse(null);
        if (approval == null || !approval.getContentDigest().equals(FeedbackApprovalDigest.of(feedback))) {
            log.error("Approved proposal content no longer matches its approval: feedbackId={}", feedback.getId());
            feedbackRepository.markApprovedSuppressed(
                event.workspaceId(),
                feedback.getId(),
                FeedbackSuppressionReason.APPROVAL_STALE.name()
            );
            return;
        }
        if (!approvalEligibility.isEligible(event.workspaceId(), feedback.getId())) {
            feedbackRepository.markApprovedSuppressed(
                event.workspaceId(),
                feedback.getId(),
                FeedbackSuppressionReason.APPROVAL_NO_LONGER_ELIGIBLE.name()
            );
            return;
        }
        AgentJob job = agentJobRepository
            .findByIdAndWorkspaceId(feedback.getAgentJobId(), event.workspaceId())
            .orElse(null);
        if (job == null || feedback.getBody() == null || feedback.getBody().isBlank()) return;

        PracticeFeedbackDeliveryPolicy.Decision<?> policy;
        if (ArtifactKinds.ISSUE.equals(feedback.getArtifactKind())) {
            policy = deliveryPolicy.evaluateIssue(job, DeliveryPolicyStage.APPROVED, feedback.getId());
        } else if (ArtifactKinds.PULL_REQUEST.equals(feedback.getArtifactKind())) {
            policy = deliveryPolicy.evaluatePullRequest(job, DeliveryPolicyStage.APPROVED, feedback.getId());
        } else {
            log.error(
                "Approved proposal has no supported artifact kind: feedbackId={}, artifactKind={}",
                feedback.getId(),
                feedback.getArtifactKind()
            );
            return;
        }
        if (!policy.allowed()) {
            if (policy.suppressionReason() != null) {
                feedbackRepository.markApprovedSuppressed(
                    event.workspaceId(),
                    feedback.getId(),
                    policy.suppressionReason().name()
                );
            }
            return;
        }
        PracticeFeedbackDispatchService.Result result = dispatchService.dispatchApproved(job, feedback);
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
            FeedbackSuppressionReason reason = result.suppressionReason();
            feedbackRepository.markApprovedSuppressed(event.workspaceId(), feedback.getId(), reason.name());
            return;
        }
        if (result.status() != PracticeFeedbackDispatchService.Result.Status.SENT) {
            log.warn("Approved proposal deferred for dispatch reconciliation: feedbackId={}", feedback.getId());
            return;
        }
        feedbackRepository.markApprovedDelivered(event.workspaceId(), feedback.getId());
    }
}

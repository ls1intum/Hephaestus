package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    private final PullRequestCommentPoster commentPoster;
    private final FeedbackApprovalEligibility approvalEligibility;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(ApprovedFeedbackReadyEvent event) {
        Feedback feedback = feedbackRepository
            .lockByIdAndWorkspaceId(event.feedbackId(), event.workspaceId())
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
            policy = deliveryPolicy.evaluateIssue(job);
        } else if (ArtifactKinds.PULL_REQUEST.equals(feedback.getArtifactKind())) {
            policy = deliveryPolicy.evaluatePullRequest(job);
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
        ExistingDeliveryLookup existing = commentPoster.findApprovedProposal(job, feedback.getId());
        if (existing.kind() == ExistingDeliveryLookup.Kind.UNKNOWN) {
            log.warn("Approved proposal deferred after inconclusive provider lookup: feedbackId={}", feedback.getId());
            return;
        }
        if (existing.kind() == ExistingDeliveryLookup.Kind.ABSENT) {
            String sanitized = PullRequestCommentPoster.sanitize(feedback.getBody());
            // A provider rejects an empty comment, and the resulting exception would escape this listener and
            // strand the approval in PREPARED with nothing to retry it.
            if (sanitized.isBlank()) {
                feedbackRepository.markApprovedSuppressed(
                    event.workspaceId(),
                    feedback.getId(),
                    FeedbackSuppressionReason.EMPTY_AFTER_SANITIZE.name()
                );
                return;
            }
            try {
                commentPoster.postApprovedProposal(job, feedback.getId(), sanitized);
            } catch (JobDeliverySuppressedException exception) {
                feedbackRepository.markApprovedSuppressed(
                    event.workspaceId(),
                    feedback.getId(),
                    FeedbackSuppressionReason.INSTANCE_SILENCED.name()
                );
                return;
            }
        }
        feedbackRepository.markApprovedDelivered(event.workspaceId(), feedback.getId());
    }
}

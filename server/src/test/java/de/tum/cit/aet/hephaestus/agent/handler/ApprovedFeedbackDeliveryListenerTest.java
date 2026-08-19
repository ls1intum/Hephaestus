package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.testconfig.TestEntities.agentJob;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.ApprovedFeedbackReadyEvent;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApproval;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalEligibility;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ApprovedFeedbackDeliveryListenerTest {

    @Test
    void shouldSuppressWhenContributingPracticeNoLongerRequiresApproval() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        FeedbackApprovalEligibility eligibility = mock(FeedbackApprovalEligibility.class);
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, UUID.randomUUID());
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            mock(AgentJobRepository.class),
            mock(PracticeFeedbackDeliveryPolicy.class),
            mock(PullRequestCommentPoster.class),
            eligibility
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(feedbackRepository).markApprovedSuppressed(7L, feedbackId, "APPROVAL_NO_LONGER_ELIGIBLE");
    }

    @Test
    void shouldTerminallySuppressApprovedProposalWhileSilentModeIsActive() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, UUID.randomUUID());
        AgentJob job = agentJob();
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);
        when(jobRepository.findByIdAndWorkspaceId(feedback.getAgentJobId(), 7L)).thenReturn(Optional.of(job));
        when(policy.evaluatePullRequest(job)).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.suppressed(FeedbackSuppressionReason.INSTANCE_SILENCED)
        );

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(feedbackRepository).markApprovedSuppressed(7L, feedbackId, "INSTANCE_SILENCED");
        verifyNoInteractions(poster);
    }

    @Test
    void shouldTerminallySuppressWhenSilentModeRacesTheProviderPost() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, UUID.randomUUID());
        AgentJob job = agentJob();
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);
        when(jobRepository.findByIdAndWorkspaceId(feedback.getAgentJobId(), 7L)).thenReturn(Optional.of(job));
        when(policy.evaluatePullRequest(job)).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.allowed(new PullRequest())
        );
        when(poster.findApprovedProposal(job, feedbackId)).thenReturn(ExistingDeliveryLookup.absent());
        doThrow(new JobDeliverySuppressedException("silent", new RuntimeException("silent")))
            .when(poster)
            .postApprovedProposal(job, feedbackId, "Exact proposal");

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(feedbackRepository).markApprovedSuppressed(7L, feedbackId, "INSTANCE_SILENCED");
        verify(feedbackRepository, never()).markApprovedDelivered(anyLong(), any());
    }

    @Test
    void shouldPostApprovedProposalExactlyOnceWhenProviderConfirmsAbsence() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, jobId);
        AgentJob job = agentJob();
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);
        when(jobRepository.findByIdAndWorkspaceId(jobId, 7L)).thenReturn(Optional.of(job));
        when(policy.evaluatePullRequest(job)).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.allowed(new PullRequest())
        );
        when(poster.findApprovedProposal(job, feedbackId)).thenReturn(ExistingDeliveryLookup.absent());

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(poster).postApprovedProposal(job, feedbackId, "Exact proposal");
        verify(feedbackRepository).markApprovedDelivered(7L, feedbackId);
    }

    @Test
    void shouldReconcileWithoutSecondPostWhenProviderAlreadyHasProposal() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, jobId);
        AgentJob job = agentJob();
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);
        when(jobRepository.findByIdAndWorkspaceId(jobId, 7L)).thenReturn(Optional.of(job));
        when(policy.evaluatePullRequest(job)).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.allowed(new PullRequest())
        );
        when(poster.findApprovedProposal(job, feedbackId)).thenReturn(ExistingDeliveryLookup.found("already-there"));

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(poster, never()).postApprovedProposal(any(), any(), any());
        verify(feedbackRepository).markApprovedDelivered(7L, feedbackId);
    }

    @Test
    void shouldPersistSuppressionWhenReleasePolicyDenies() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        AgentJob job = agentJob();
        Feedback feedback = proposal(feedbackId, jobId);
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        approve(approvalRepository, feedback);
        when(jobRepository.findByIdAndWorkspaceId(jobId, 7L)).thenReturn(Optional.of(job));
        when(policy.evaluatePullRequest(job)).thenReturn(
            PracticeFeedbackDeliveryPolicy.Decision.suppressed(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT)
        );

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verifyNoInteractions(poster);
        verify(feedbackRepository, never()).markApprovedDelivered(anyLong(), any());
        verify(feedbackRepository).markApprovedSuppressed(
            7L,
            feedbackId,
            FeedbackSuppressionReason.RECIPIENT_OPTED_OUT.name()
        );
    }

    @Test
    void shouldSuppressProposalWhenApprovedContentNoLongerMatches() {
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        FeedbackApprovalRepository approvalRepository = mock(FeedbackApprovalRepository.class);
        AgentJobRepository jobRepository = mock(AgentJobRepository.class);
        PracticeFeedbackDeliveryPolicy policy = mock(PracticeFeedbackDeliveryPolicy.class);
        PullRequestCommentPoster poster = mock(PullRequestCommentPoster.class);
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = proposal(feedbackId, UUID.randomUUID());
        when(feedbackRepository.lockByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        when(approvalRepository.findByFeedbackIdAndWorkspaceId(feedbackId, 7L)).thenReturn(
            Optional.of(
                FeedbackApproval.builder().feedbackId(feedbackId).workspaceId(7L).contentDigest("0".repeat(64)).build()
            )
        );

        new ApprovedFeedbackDeliveryListener(
            feedbackRepository,
            approvalRepository,
            jobRepository,
            policy,
            poster,
            eligible()
        ).deliver(new ApprovedFeedbackReadyEvent(7L, feedbackId));

        verify(feedbackRepository).markApprovedSuppressed(
            7L,
            feedbackId,
            FeedbackSuppressionReason.APPROVAL_STALE.name()
        );
        verifyNoInteractions(jobRepository, policy, poster);
    }

    private static void approve(FeedbackApprovalRepository repository, Feedback feedback) {
        when(repository.findByFeedbackIdAndWorkspaceId(feedback.getId(), 7L)).thenReturn(
            Optional.of(
                FeedbackApproval.builder()
                    .feedbackId(feedback.getId())
                    .workspaceId(7L)
                    .contentDigest(FeedbackApprovalDigest.of(feedback))
                    .build()
            )
        );
    }

    private static FeedbackApprovalEligibility eligible() {
        FeedbackApprovalEligibility eligibility = mock(FeedbackApprovalEligibility.class);
        when(eligibility.isEligible(anyLong(), any())).thenReturn(true);
        return eligibility;
    }

    private static Feedback proposal(UUID feedbackId, UUID jobId) {
        return Feedback.builder()
            .id(feedbackId)
            .agentJobId(jobId)
            .workspaceId(7L)
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .recipientUserId(8L)
            .aboutUserId(8L)
            .channel(FeedbackChannel.IN_CONTEXT)
            .position(7_000)
            .deliveryState(FeedbackDeliveryState.PREPARED)
            .body("Exact proposal")
            .source(FeedbackSource.AGENT)
            .build();
    }
}

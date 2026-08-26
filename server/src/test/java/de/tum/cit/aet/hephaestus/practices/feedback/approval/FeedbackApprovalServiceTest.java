package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.DecideFeedbackProposalRequestDTO;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedbackApprovalServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackApprovalRepository approvalRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FeedbackApprovalEligibility eligibility;

    private FeedbackApprovalService service;
    private UUID feedbackId;

    @BeforeEach
    void setUp() {
        service = new FeedbackApprovalService(feedbackRepository, approvalRepository, eventPublisher, eligibility);
        feedbackId = UUID.randomUUID();
        Feedback feedback = Feedback.builder()
            .id(feedbackId)
            .agentJobId(UUID.randomUUID())
            .workspaceId(7L)
            .recipientUserId(11L)
            .aboutUserId(11L)
            .channel(FeedbackChannel.IN_CONTEXT)
            .position(1)
            .deliveryState(FeedbackDeliveryState.AWAITING_APPROVAL)
            .body("Useful feedback")
            .source(FeedbackSource.AGENT)
            .build();
        when(feedbackRepository.findByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(feedback));
        lenient().when(eligibility.isEligible(7L, feedbackId)).thenReturn(true);
        lenient()
            .when(approvalRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRefuseApprovalWhileSendingIsPausedAndLeaveTheProposalDecidable() {
        when(eligibility.brakeOnDelivery(7L)).thenReturn(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED);

        assertThatThrownBy(() ->
            service.decide(
                7L,
                feedbackId,
                42L,
                new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null)
            )
        ).isInstanceOf(ResponseStatusException.class);

        verify(feedbackRepository, never()).decideProposal(any(), any(), any());
        verify(feedbackRepository, never()).suppressProposal(any(), any(), any());
    }

    @Test
    void shouldRefuseApprovalUnderInstanceSilence() {
        when(eligibility.brakeOnDelivery(7L)).thenReturn(FeedbackSuppressionReason.INSTANCE_SILENCED);

        assertThatThrownBy(() ->
            service.decide(
                7L,
                feedbackId,
                42L,
                new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null)
            )
        ).isInstanceOf(ResponseStatusException.class);

        verify(feedbackRepository, never()).decideProposal(any(), any(), any());
    }

    @Test
    void shouldStillAllowRejectionWhileSendingIsPaused() {
        lenient().when(eligibility.brakeOnDelivery(7L)).thenReturn(FeedbackSuppressionReason.WORKSPACE_DELIVERY_PAUSED);
        when(feedbackRepository.decideProposal(7L, feedbackId, "DISCARDED")).thenReturn(1);

        service.decide(
            7L,
            feedbackId,
            42L,
            new DecideFeedbackProposalRequestDTO(
                FeedbackApprovalDecision.REJECTED,
                FeedbackRejectionReason.UNHELPFUL,
                null
            )
        );

        verify(feedbackRepository).decideProposal(7L, feedbackId, "DISCARDED");
    }

    @Test
    void shouldQueueExactProposalWhenApproved() {
        when(feedbackRepository.decideProposal(7L, feedbackId, "PREPARED")).thenReturn(1);

        FeedbackApproval result = service.decide(
            7L,
            feedbackId,
            42L,
            new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null)
        );

        verify(feedbackRepository).decideProposal(7L, feedbackId, "PREPARED");
        assertThat(result.getActorAccountId()).isEqualTo(42L);
        assertThat(result.getContentDigest()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldSuppressWhenProposalNoLongerRequiresApproval() {
        when(eligibility.isEligible(7L, feedbackId)).thenReturn(false);

        assertThatThrownBy(() ->
            service.decide(
                7L,
                feedbackId,
                42L,
                new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null)
            )
        ).isInstanceOf(ResponseStatusException.class);

        verify(feedbackRepository).suppressProposal(7L, feedbackId, "APPROVAL_NO_LONGER_ELIGIBLE");
    }

    @Test
    void shouldRecordReasonWhenRejected() {
        when(feedbackRepository.decideProposal(7L, feedbackId, "DISCARDED")).thenReturn(1);

        service.decide(
            7L,
            feedbackId,
            42L,
            new DecideFeedbackProposalRequestDTO(
                FeedbackApprovalDecision.REJECTED,
                FeedbackRejectionReason.MISSING_CONTEXT,
                "The feedback overlooks the fallback path."
            )
        );

        ArgumentCaptor<FeedbackApproval> captor = ArgumentCaptor.forClass(FeedbackApproval.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().getRejectionReason()).isEqualTo(FeedbackRejectionReason.MISSING_CONTEXT);
        assertThat(captor.getValue().getRejectionNote()).isEqualTo("The feedback overlooks the fallback path.");
    }

    @Test
    void shouldRejectConcurrentSecondDecision() {
        when(feedbackRepository.decideProposal(7L, feedbackId, "PREPARED")).thenReturn(0);

        assertThatThrownBy(() ->
            service.decide(
                7L,
                feedbackId,
                42L,
                new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.APPROVED, null, null)
            )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldAllowRejectionWithoutCategory() {
        when(feedbackRepository.decideProposal(7L, feedbackId, "DISCARDED")).thenReturn(1);
        FeedbackApproval result = service.decide(
            7L,
            feedbackId,
            42L,
            new DecideFeedbackProposalRequestDTO(FeedbackApprovalDecision.REJECTED, null, null)
        );
        assertThat(result.getRejectionReason()).isNull();
    }
}

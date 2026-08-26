package de.tum.cit.aet.hephaestus.agent.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.DeliveryStatus;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

class PracticeFeedbackDispatchRecoveryTest extends BaseUnitTest {

    @Mock
    private FeedbackDispatchRepository dispatches;

    @Mock
    private AgentJobRepository jobs;

    @Mock
    private FeedbackRepository feedback;

    @Mock
    private PracticeFeedbackDispatchService service;

    @Mock
    private FeedbackLedgerRecorder ledgerRecorder;

    private PracticeFeedbackDispatchRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new PracticeFeedbackDispatchRecovery(dispatches, jobs, feedback, service, ledgerRecorder);
    }

    @Test
    void exhaustedApprovedDispatchTerminalizesBothDispatchAndFeedback() {
        FeedbackDispatch dispatch = dispatch(FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE, UUID.randomUUID());
        when(dispatches.findExhausted(any(), anyInt(), any())).thenReturn(List.of(dispatch));

        recovery.recover();

        verify(service).fail(dispatch, "Dispatch retry limit exhausted");
        verify(feedback).markApprovedFailed(dispatch.getWorkspaceId(), dispatch.approvedFeedbackId());
    }

    @Test
    void recoveredApprovedDeliveryReconcilesTheFeedbackLifecycle() {
        UUID feedbackId = UUID.randomUUID();
        FeedbackDispatch dispatch = dispatch(FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE, feedbackId);
        AgentJob job = new AgentJob();
        var unit = mock(de.tum.cit.aet.hephaestus.practices.feedback.Feedback.class);
        when(unit.getBody()).thenReturn(dispatch.getBody());
        when(dispatches.findRecoverable(any(), anyInt(), any())).thenReturn(List.of(dispatch));
        when(dispatches.findByIdAndWorkspaceId(dispatch.getId(), 7L)).thenReturn(Optional.of(dispatch));
        when(jobs.findByIdAndWorkspaceId(dispatch.getAgentJobId(), 7L)).thenReturn(Optional.of(job));
        when(feedback.findByIdAndWorkspaceId(feedbackId, 7L)).thenReturn(Optional.of(unit));
        when(service.recover(dispatch, job)).thenReturn(PracticeFeedbackDispatchService.Result.sent("provider-42"));

        recovery.recover();

        verify(feedback).markApprovedDelivered(7L, feedbackId);
    }

    @Test
    void recoveredAutomaticDeliveryReconcilesTheJobLifecycle() {
        FeedbackDispatch dispatch = dispatch(FeedbackDispatchDestination.ARTIFACT_SUMMARY, null);
        AgentJob job = new AgentJob();
        when(dispatches.findRecoverable(any(), anyInt(), any())).thenReturn(List.of(dispatch));
        when(dispatches.findByIdAndWorkspaceId(dispatch.getId(), 7L)).thenReturn(Optional.of(dispatch));
        when(jobs.findByIdAndWorkspaceId(dispatch.getAgentJobId(), 7L)).thenReturn(Optional.of(job));
        when(service.recover(dispatch, job)).thenReturn(PracticeFeedbackDispatchService.Result.sent("provider-42"));

        recovery.recover();

        verify(jobs).reconcileDispatchDeliveryStatus(
            dispatch.getAgentJobId(),
            7L,
            DeliveryStatus.DELIVERED,
            "provider-42"
        );
    }

    private static FeedbackDispatch dispatch(FeedbackDispatchDestination destination, @Nullable UUID feedbackId) {
        return dispatch(destination, feedbackId, false);
    }

    private static FeedbackDispatch dispatch(
        FeedbackDispatchDestination destination,
        @Nullable UUID feedbackId,
        boolean writeStarted
    ) {
        UUID id = UUID.randomUUID();
        return new FeedbackDispatch(
            id,
            "dispatch:" + id,
            7L,
            UUID.randomUUID(),
            feedbackId,
            destination,
            FeedbackDispatchState.UNCERTAIN,
            "body",
            null,
            JsonMapper.builder().build().valueToTree(List.of("practice")),
            writeStarted,
            null,
            null,
            null,
            Instant.now(),
            1,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}

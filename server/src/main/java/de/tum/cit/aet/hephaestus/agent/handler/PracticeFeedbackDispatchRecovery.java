package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.DeliveryStatus;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
@WorkspaceAgnostic("Each recovered dispatch reloads its job and optional feedback through the row's tenant key")
class PracticeFeedbackDispatchRecovery {

    private static final Logger log = LoggerFactory.getLogger(PracticeFeedbackDispatchRecovery.class);
    private static final int BATCH_SIZE = 50;

    private final FeedbackDispatchRepository dispatchRepository;
    private final AgentJobRepository agentJobRepository;
    private final FeedbackRepository feedbackRepository;
    private final PracticeFeedbackDispatchService dispatchService;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    @SchedulerLock(name = "practice-feedback-dispatch-recovery", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
    void recover() {
        for (var exhausted : dispatchRepository.findExhausted(
            Instant.now(),
            PracticeFeedbackDispatchService.MAX_ATTEMPTS,
            PageRequest.of(0, BATCH_SIZE)
        )) {
            try {
                fail(exhausted, "Dispatch retry limit exhausted");
            } catch (RuntimeException exception) {
                log.warn(
                    "Exhausted practice feedback dispatch could not be failed: dispatchId={}",
                    exhausted.getId(),
                    exception
                );
            }
        }
        for (var candidate : dispatchRepository.findRecoverable(
            Instant.now(),
            PracticeFeedbackDispatchService.MAX_ATTEMPTS,
            PageRequest.of(0, BATCH_SIZE)
        )) {
            try {
                var dispatch = dispatchRepository
                    .findByIdAndWorkspaceId(candidate.getId(), candidate.getWorkspaceId())
                    .orElse(null);
                if (dispatch == null) continue;
                var job = agentJobRepository
                    .findByIdAndWorkspaceId(dispatch.getAgentJobId(), dispatch.getWorkspaceId())
                    .orElse(null);
                if (job == null) {
                    fail(dispatch, "Dispatch job no longer exists");
                    continue;
                }
                if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE) {
                    var feedback = feedbackRepository
                        .findByIdAndWorkspaceId(dispatch.approvedFeedbackId(), dispatch.getWorkspaceId())
                        .orElse(null);
                    if (
                        feedback == null || feedback.getBody() == null || !feedback.getBody().equals(dispatch.getBody())
                    ) {
                        fail(dispatch, "Approved feedback is missing or no longer matches its immutable body");
                        continue;
                    }
                }
                var result = dispatchService.recover(dispatch, job);
                reconcileDomain(dispatch, result);
            } catch (RuntimeException exception) {
                log.warn("Practice feedback dispatch recovery deferred: dispatchId={}", candidate.getId(), exception);
            }
        }
    }

    private void reconcileDomain(FeedbackDispatch dispatch, PracticeFeedbackDispatchService.Result result) {
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE) {
            if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
                feedbackRepository.markApprovedDelivered(dispatch.getWorkspaceId(), dispatch.approvedFeedbackId());
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
                if (result.externalRef() == null) {
                    feedbackRepository.markApprovedSuppressed(
                        dispatch.getWorkspaceId(),
                        dispatch.approvedFeedbackId(),
                        result.refusal().name()
                    );
                } else {
                    feedbackRepository.markApprovedPartiallyDelivered(
                        dispatch.getWorkspaceId(),
                        dispatch.approvedFeedbackId(),
                        result.refusal().name()
                    );
                }
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.FAILED) {
                feedbackRepository.markApprovedFailed(dispatch.getWorkspaceId(), dispatch.approvedFeedbackId());
            } else if (result.externalRef() != null) {
                feedbackRepository.markApprovedPartiallyDelivered(
                    dispatch.getWorkspaceId(),
                    dispatch.approvedFeedbackId(),
                    null
                );
            }
            return;
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
            agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                DeliveryStatus.DELIVERED,
                result.sentRef()
            );
            agentJobRepository
                .findById(dispatch.getAgentJobId())
                .ifPresent(job ->
                    feedbackLedgerRecorder.recordRecoveredSummary(job, result.sentRef(), dispatch.getBody())
                );
        } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.FAILED) {
            agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                DeliveryStatus.FAILED,
                null
            );
        }
    }

    private void fail(FeedbackDispatch dispatch, String error) {
        dispatchService.fail(dispatch, error);
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_REVIEW_PACKAGE) {
            feedbackRepository.markApprovedFailed(dispatch.getWorkspaceId(), dispatch.approvedFeedbackId());
        } else {
            agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                DeliveryStatus.FAILED,
                null
            );
        }
    }
}

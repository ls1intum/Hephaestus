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

    /** How long an approved proposal waits for an operator to lift the brake before it is dropped. */
    private static final java.time.Duration HELD_TTL = java.time.Duration.ofDays(30);

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    @SchedulerLock(name = "practice-feedback-dispatch-recovery", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
    void recover() {
        for (var exhausted : dispatchRepository.findExhausted(
            Instant.now(),
            PracticeFeedbackDispatchService.MAX_ATTEMPTS,
            PageRequest.of(0, BATCH_SIZE)
        )) {
            try {
                failUnlessProviderWriteMayHaveLanded(exhausted, "Dispatch retry limit exhausted");
            } catch (RuntimeException exception) {
                log.warn(
                    "Exhausted practice feedback dispatch could not be failed: dispatchId={}",
                    exhausted.getId(),
                    exception
                );
            }
        }
        for (var stale : dispatchRepository.findHeldSince(Instant.now().minus(HELD_TTL))) {
            if (dispatchRepository.giveUpOnHeld(stale.getId(), stale.getWorkspaceId()) == 1) {
                projectGiveUp(stale);
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
                    failUnlessProviderWriteMayHaveLanded(dispatch, "Dispatch job no longer exists");
                    continue;
                }
                if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
                    var feedback = feedbackRepository
                        .findByIdAndWorkspaceId(dispatch.getFeedbackId(), dispatch.getWorkspaceId())
                        .orElse(null);
                    if (
                        feedback == null || feedback.getBody() == null || !feedback.getBody().equals(dispatch.getBody())
                    ) {
                        failUnlessProviderWriteMayHaveLanded(
                            dispatch,
                            "Approved feedback is missing or no longer matches its immutable body"
                        );
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

    private void projectGiveUp(FeedbackDispatch dispatch) {
        log.info(
            "Held dispatch dropped after {}: dispatchId={}, reason={}",
            HELD_TTL,
            dispatch.getId(),
            dispatch.getSuppressionReason()
        );
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
            feedbackRepository.markApprovedSuppressed(
                dispatch.getWorkspaceId(),
                dispatch.getFeedbackId(),
                dispatch.getSuppressionReason()
            );
        }
    }

    private void reconcileDomain(FeedbackDispatch dispatch, PracticeFeedbackDispatchService.Result result) {
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
            if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
                feedbackRepository.markApprovedDelivered(dispatch.getWorkspaceId(), dispatch.getFeedbackId());
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
                feedbackRepository.markApprovedSuppressed(
                    dispatch.getWorkspaceId(),
                    dispatch.getFeedbackId(),
                    result.suppressionReason().name()
                );
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.FAILED) {
                feedbackRepository.markApprovedFailed(dispatch.getWorkspaceId(), dispatch.getFeedbackId());
            }
            return;
        }
        if (dispatch.getDestination() == FeedbackDispatchDestination.RE_REVIEW_PING) {
            // Cosmetic: the summary it points at carries the review, so a lost ping is not a lost delivery.
            return;
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
            agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                DeliveryStatus.DELIVERED,
                result.externalRef()
            );
            agentJobRepository
                .findById(dispatch.getAgentJobId())
                .ifPresent(job ->
                    feedbackLedgerRecorder.recordRecoveredSummary(job, result.externalRef(), dispatch.getBody())
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

    private void failUnlessProviderWriteMayHaveLanded(FeedbackDispatch dispatch, String error) {
        if (Boolean.TRUE.equals(dispatch.getWriteStarted())) {
            log.warn(
                "Dispatch not failed because its provider write may already be live; leaving it to keep retrying: dispatchId={}, error={}",
                dispatch.getId(),
                error
            );
            return;
        }
        dispatchService.fail(dispatch, error);
        if (
            dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT &&
            dispatch.getFeedbackId() != null
        ) {
            feedbackRepository.markApprovedFailed(dispatch.getWorkspaceId(), dispatch.getFeedbackId());
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

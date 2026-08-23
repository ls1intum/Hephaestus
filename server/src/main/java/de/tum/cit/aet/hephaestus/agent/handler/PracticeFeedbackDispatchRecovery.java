package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.DeliveryStatus;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchDestination;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
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

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    @SchedulerLock(name = "practice-feedback-dispatch-recovery", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
    void recover() {
        for (var exhausted : dispatchRepository.findExhausted(
            Instant.now(),
            PracticeFeedbackDispatchService.MAX_ATTEMPTS,
            PageRequest.of(0, BATCH_SIZE)
        )) {
            try {
                terminalize(exhausted, "Dispatch retry limit exhausted");
            } catch (RuntimeException exception) {
                log.warn(
                    "Exhausted practice feedback dispatch could not be terminalized: dispatchId={}",
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
                    terminalize(dispatch, "Dispatch job no longer exists");
                    continue;
                }
                if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
                    var feedback = feedbackRepository
                        .findByIdAndWorkspaceId(dispatch.getFeedbackId(), dispatch.getWorkspaceId())
                        .orElse(null);
                    if (
                        feedback == null || feedback.getBody() == null || !feedback.getBody().equals(dispatch.getBody())
                    ) {
                        terminalize(dispatch, "Approved feedback is missing or no longer matches its immutable body");
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
        if (dispatch.getDestination() == FeedbackDispatchDestination.APPROVED_ARTIFACT_COMMENT) {
            if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
                feedbackRepository.markApprovedDelivered(dispatch.getWorkspaceId(), dispatch.getFeedbackId());
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.SUPPRESSED) {
                var reason =
                    result.suppressionReason() == null
                        ? FeedbackSuppressionReason.APPROVAL_NO_LONGER_ELIGIBLE
                        : result.suppressionReason();
                feedbackRepository.markApprovedSuppressed(
                    dispatch.getWorkspaceId(),
                    dispatch.getFeedbackId(),
                    reason.name()
                );
            } else if (result.status() == PracticeFeedbackDispatchService.Result.Status.FAILED) {
                feedbackRepository.markApprovedFailed(dispatch.getWorkspaceId(), dispatch.getFeedbackId());
            }
            return;
        }
        if (result.status() == PracticeFeedbackDispatchService.Result.Status.SENT) {
            agentJobRepository.reconcileDispatchDeliveryStatus(
                dispatch.getAgentJobId(),
                dispatch.getWorkspaceId(),
                DeliveryStatus.DELIVERED,
                result.externalRef()
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

    /**
     * Gives up on a dispatch and says so in the ledger.
     *
     * <p>Only for a dispatch that never began its provider write. Once {@code write_started} is set the
     * comment may be live on the artifact, and the lookup that would prove it can answer {@code UNKNOWN}
     * for reasons of its own — a rate limit, a search budget. Recording FAILED there would tell a
     * developer their feedback was lost while it sits on their pull request, and would clear the
     * comment id the next review needs to edit in place. Those keep retrying the lookup instead.
     */
    private void terminalize(FeedbackDispatch dispatch, String error) {
        if (Boolean.TRUE.equals(dispatch.getWriteStarted())) {
            log.warn(
                "Dispatch exhausted after a provider write may have landed; leaving it for reconciliation: dispatchId={}, error={}",
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

package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.PendingSignalResubmitter;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offers a pending pull-request signal back to the gate and the submission path.
 *
 * <p>Deliberately replays the whole decision rather than the refusal that blocked it: the workspace
 * may have changed its mind about drafts, or retired the practice, in the time the signal waited. If
 * it still refuses, the submission path records the current reason, which restamps the wait and
 * therefore paces the next attempt.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
public class PullRequestSignalResubmitter implements PendingSignalResubmitter {

    private static final Logger log = LoggerFactory.getLogger(PullRequestSignalResubmitter.class);

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;
    private final SignalRecorder signalRecorder;

    public PullRequestSignalResubmitter(
        AgentJobService agentJobService,
        PullRequestRepository pullRequestRepository,
        PracticeReviewDetectionGate practiceReviewDetectionGate,
        SignalRecorder signalRecorder
    ) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
        this.signalRecorder = signalRecorder;
    }

    @Override
    public ArtifactKind artifactKind() {
        return ScmSignals.PULL_REQUEST;
    }

    /**
     * Its own transaction so one signal's failure cannot unwind the rest of the sweep, and so the
     * submission path's idempotency-race rollback stays confined to this signal.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resubmit(ArtifactSignal signal) {
        SignalKey key = signal.key();
        PullRequest pr = pullRequestRepository.findByIdWithAllForGate(key.artifactId()).orElse(null);
        if (pr == null || pr.getHeadRefName() == null || pr.getHeadRefOid() == null || pr.getBaseRefName() == null) {
            log.debug("Pending signal has no reviewable pull request left: prId={}", key.artifactId());
            signalRecorder.markRefused(key, SignalStateReason.ARTIFACT_GONE);
            return;
        }

        switch (practiceReviewDetectionGate.evaluate(pr, key.signalName(), TriggerMode.AUTO)) {
            case GateDecision.Skip skip -> {
                log.debug("Pending signal now skipped by practice gate: prId={}, reason={}", pr.getId(), skip.reason());
                signalRecorder.markRefused(key, SignalStateReason.GATE_SKIPPED);
            }
            case GateDecision.Detect detect -> agentJobService.submit(
                detect.workspace().getId(),
                AgentJobType.PULL_REQUEST_REVIEW,
                new PullRequestReviewSubmissionRequest(
                    ScmEventPayload.PullRequestData.from(pr),
                    pr.getHeadRefName(),
                    pr.getHeadRefOid(),
                    pr.getBaseRefName(),
                    key.signalName()
                ),
                key
            );
        }
    }
}

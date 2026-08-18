package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.conversation.ChatSignals;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationCandidateSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.agent.handler.ConversationReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.PendingSignalResubmitter;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns one settled-thread occurrence into reviews, and settles its ledger row either way. Two entry
 * points share this body: the sweep raises the occurrence, and the reaper re-offers one that was refused
 * for something an operator can undo — the second attempt must reach exactly the decision the first would
 * have.
 *
 * <h2>One row, several jobs</h2>
 * <p>A review is filed per participant (findings about how a person writes go to that person), but the
 * signal happened once — the thread settled — so the ledger gets one row regardless of fan-out width.
 * That row settles as triggered if any participant's review started, otherwise refused with the first
 * reason that stopped one; a partial fan-out still counts as triggered, and the participants who missed
 * out are visible as missing jobs rather than as a signal claiming nothing happened.
 */
@ConditionalOnServerRole
@Component
public class ConversationReviewSubmitter implements PendingSignalResubmitter {

    private static final Logger log = LoggerFactory.getLogger(ConversationReviewSubmitter.class);

    private final ConversationCandidateSource candidateSource;
    private final AgentJobService agentJobService;
    private final SignalRecorder signalRecorder;
    private final TransactionTemplate transactionTemplate;

    public ConversationReviewSubmitter(
        ConversationCandidateSource candidateSource,
        AgentJobService agentJobService,
        SignalRecorder signalRecorder,
        TransactionTemplate transactionTemplate
    ) {
        this.candidateSource = candidateSource;
        this.agentJobService = agentJobService;
        this.signalRecorder = signalRecorder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ArtifactKind artifactKind() {
        return ChatSignals.CONVERSATION_THREAD;
    }

    /**
     * The thread is re-read rather than reconstructed from the ledger row, so consent is re-checked: a
     * channel withdrawn since the signal was recorded stops producing reviews here, immediately.
     */
    @Override
    public void resubmit(ArtifactSignal signal) {
        long workspaceId = signal.getWorkspace().getId();
        ConversationThreadCandidate candidate = candidateSource
            .candidateById(workspaceId, signal.getArtifactId())
            .orElse(null);
        if (candidate == null) {
            log.debug(
                "Conversation signal has no consented thread left to review: workspaceId={}, threadId={}",
                workspaceId,
                signal.getArtifactId()
            );
            settleRefused(signal.key(), SignalStateReason.ARTIFACT_GONE);
            return;
        }
        submitAndSettle(candidate, signal.key());
    }

    /**
     * Not transactional: {@link AgentJobService#submit} states that callers must not wrap it, so the two
     * settle calls open transactions of their own — {@code SignalRecorder} is {@code MANDATORY} and would
     * otherwise throw at exactly the moment there is a decision to record.
     *
     * @return how many reviews started
     */
    public long submitAndSettle(ConversationThreadCandidate candidate, SignalKey key) {
        long started = 0;
        UUID firstJobId = null;
        SignalStateReason firstRefusal = null;
        for (long participant : candidate.participantMemberIds()) {
            if (participant <= 0) {
                continue;
            }
            try {
                SubmissionOutcome outcome = agentJobService.submitWithOutcome(
                    candidate.workspaceId(),
                    AgentJobType.CONVERSATION_REVIEW,
                    requestFor(candidate, participant),
                    // Null: the ledger row belongs to the occurrence, not to any one recipient.
                    null
                );
                if (outcome.job() != null) {
                    started++;
                    if (firstJobId == null) {
                        firstJobId = outcome.job().getId();
                    }
                } else if (firstRefusal == null) {
                    firstRefusal = outcome.requireRefusal();
                }
            } catch (RuntimeException e) {
                log.warn(
                    "conversation.detect: enqueue failed for threadId={}, participant={}: {}",
                    candidate.threadId(),
                    participant,
                    e.toString()
                );
            }
        }

        if (firstJobId != null) {
            settleTriggered(key, firstJobId);
        } else {
            // No reason means every participant threw or none was resolvable; SUBJECT_UNLINKED holds the
            // signal open for the reaper rather than retiring it.
            settleRefused(key, firstRefusal != null ? firstRefusal : SignalStateReason.SUBJECT_UNLINKED);
        }
        return started;
    }

    private ConversationReviewSubmissionRequest requestFor(ConversationThreadCandidate candidate, long participant) {
        return new ConversationReviewSubmissionRequest(
            candidate.threadId(),
            candidate.channelId(),
            candidate.channelName(),
            candidate.threadTs(),
            participant,
            candidate.lastTs()
        );
    }

    private void settleTriggered(SignalKey key, UUID jobId) {
        transactionTemplate.executeWithoutResult(status -> signalRecorder.markTriggered(key, jobId));
    }

    private void settleRefused(SignalKey key, SignalStateReason reason) {
        transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(key, reason));
    }
}

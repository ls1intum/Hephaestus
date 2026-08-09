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
 * Turns one settled-thread occurrence into reviews, and settles its ledger row either way.
 *
 * <p>Two entry points onto the same body, for the same reason {@code DocumentReviewSubmitter} has two:
 * the sweep raises the occurrence, the reaper re-offers one that was refused for something an operator
 * can undo, and the second attempt has to reach exactly the decision the first would have.
 *
 * <h2>One row, several jobs</h2>
 * <p>A conversation review is filed per participant, because findings about how a person writes are
 * delivered to that person. The <em>signal</em> still happened once: the thread settled. So the ledger
 * gets one row and the fan-out gets none — the number of recipients is a delivery decision, and making
 * it a number of occurrences would put the participant count into every "how many occasions did this
 * instance see" answer.
 *
 * <p>The row is settled once, from the fan-out as a whole: triggered if any participant's review
 * started, otherwise refused with the first reason that stopped one. A partial fan-out counts as
 * triggered — a review did run on this occurrence — and the participants who missed out are visible as
 * the missing jobs rather than as a signal that claims nothing happened.
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
     * Re-offer a settled-thread signal the reaper is holding open.
     *
     * <p>The thread is re-read rather than reconstructed from the ledger row, so consent is re-checked:
     * a channel withdrawn since the signal was recorded stops producing reviews here, immediately, and
     * the signal is retired rather than left to be re-offered against a conversation nobody agreed to.
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
     * Fan the occurrence out to its participants and settle its ledger row with what came of that.
     *
     * <p><strong>Not transactional.</strong> {@link AgentJobService#submit} states that callers must not
     * wrap it, so the two settle calls open transactions of their own — {@code SignalRecorder} is
     * {@code MANDATORY} and would otherwise throw at exactly the moment there is a decision to record.
     *
     * @param key the row already recorded for this occurrence, which this call now owns
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
                    // Null on purpose: the ledger row belongs to the occurrence, not to any one
                    // recipient, and letting each submission settle it would leave the row pointing at
                    // whichever participant happened to be last.
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
            // Nothing started and nothing named a reason: every participant threw, or the thread has no
            // resolvable participant at all. Held open rather than retired — the people in it linking
            // their accounts is precisely the operator-liftable condition the reaper exists for.
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

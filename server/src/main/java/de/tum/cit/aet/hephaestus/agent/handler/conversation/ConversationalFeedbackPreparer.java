package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the PREPARED CONVERSATION feedback units for a cycle's admitted observations (S7). A prepared unit is a
 * standing "raise this next" marker: {@code channel=CONVERSATION}, {@code deliveryState=PREPARED}, and a deliberately
 * NULL body - the mentor composes the student-facing wording at delivery, so no stale snippet is frozen at
 * preparation time. Each unit's recipient is its own observation's {@code about_user_id} (per-observation).
 *
 * <p>Bounded (top-N=3 per recipient). Selection is deterministic (severity, then confidence, then id) so a re-run of
 * the same job re-derives the same units at the same {@code (agent_job_id, position)} grain and the
 * {@code existsByAgentJobIdAndPosition} guard makes preparation idempotent. Positions start at
 * {@link FeedbackLedgerRecorder#CONVERSATION_UNIT_ORDINAL_BASE} so they never collide with the IN_CONTEXT /
 * suppressed / policy-floor units of the same job.
 */
@Component
public class ConversationalFeedbackPreparer {

    private static final Logger log = LoggerFactory.getLogger(ConversationalFeedbackPreparer.class);

    /** Cap on prepared conversational units per recipient per cycle - bounds the mentor's "raise next" queue. */
    static final int TOP_N_PER_RECIPIENT = 3;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;

    public ConversationalFeedbackPreparer(
        FeedbackRepository feedbackRepository,
        FeedbackObservationRepository feedbackObservationRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackObservationRepository = feedbackObservationRepository;
    }

    /**
     * Prepare PREPARED CONVERSATION units for {@code admitted} observations of a job. Runs REQUIRES_NEW so a
     * preparation failure is isolated; idempotent on a re-run via the {@code (agent_job_id, position)} guard.
     *
     * @return the number of units newly prepared this call (0 on a pure re-run)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int prepare(UUID agentJobId, Long workspaceId, List<Observation> admitted) {
        if (admitted.isEmpty()) {
            return 0;
        }
        List<Observation> ordered = admitted
            .stream()
            .sorted(
                Comparator.comparingLong(Observation::getAboutUserId)
                    .thenComparingInt(ConversationalFeedbackPreparer::severityOrdinal)
                    .thenComparing(Comparator.comparing(ConversationalFeedbackPreparer::confidenceOf).reversed())
                    .thenComparing(o -> o.getId().toString())
            )
            .collect(Collectors.toList());

        Map<Long, Integer> perRecipientCount = new HashMap<>();
        Instant now = Instant.now();
        int position = FeedbackLedgerRecorder.CONVERSATION_UNIT_ORDINAL_BASE;
        int prepared = 0;
        for (Observation observation : ordered) {
            long recipient = observation.getAboutUserId();
            int count = perRecipientCount.getOrDefault(recipient, 0);
            if (count >= TOP_N_PER_RECIPIENT) {
                continue;
            }
            perRecipientCount.put(recipient, count + 1);
            int unitPosition = position++;
            if (feedbackRepository.existsByAgentJobIdAndPosition(agentJobId, unitPosition)) {
                continue;
            }
            Feedback unit = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(agentJobId)
                    .workspaceId(workspaceId)
                    .artifactType(observation.getArtifactType())
                    .artifactId(observation.getArtifactId())
                    .recipientUserId(recipient)
                    .aboutUserId(recipient)
                    .channel(FeedbackChannel.CONVERSATION)
                    .position(unitPosition)
                    .deliveryState(FeedbackDeliveryState.PREPARED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(now)
                    .build()
            );
            feedbackObservationRepository.insertIfAbsent(
                unit.getId(),
                observation.getId(),
                EvidenceRole.PRIMARY.name(),
                0
            );
            prepared++;
        }
        if (prepared > 0) {
            log.info("Conversational feedback prepared: jobId={}, units={}", agentJobId, prepared);
        }
        return prepared;
    }

    private static int severityOrdinal(Observation observation) {
        Severity severity = observation.getSeverity();
        return severity == null ? Integer.MAX_VALUE : severity.ordinal();
    }

    private static float confidenceOf(Observation observation) {
        return observation.getConfidence() == null ? 0f : observation.getConfidence();
    }
}

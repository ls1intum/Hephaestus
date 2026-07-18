package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the PREPARED CONVERSATION feedback units for a cycle's admitted observations. A prepared unit is a
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
    private final ApplicationEventPublisher eventPublisher;

    public ConversationalFeedbackPreparer(
        FeedbackRepository feedbackRepository,
        FeedbackObservationRepository feedbackObservationRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.eventPublisher = eventPublisher;
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

        // Every admitted observation now consumes a slot (raised or withheld), so the band is no longer
        // bounded by the per-recipient cap. Overflowing it would collide with the next band and silently drop
        // the very rows this records — fail loud instead; a job with this many admitted loci is pathological.
        if (ordered.size() > FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH) {
            throw new IllegalStateException(
                "Conversational units exceed the ordinal band: jobId=" +
                    agentJobId +
                    ", admitted=" +
                    ordered.size() +
                    ", band=" +
                    FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH
            );
        }

        Map<Long, Integer> perRecipientCount = new HashMap<>();
        // Newly CREATED units only (re-run no-ops excluded) — feeds the per-recipient prepared event.
        Map<Long, Integer> newlyPreparedByRecipient = new HashMap<>();
        Instant now = Instant.now();
        int position = FeedbackLedgerRecorder.CONVERSATION_UNIT_ORDINAL_BASE;
        int prepared = 0;
        for (Observation observation : ordered) {
            long recipient = observation.getAboutUserId();
            int count = perRecipientCount.getOrDefault(recipient, 0);
            // Over the per-recipient cap the locus is withheld, not raised. The router already established
            // nobody has seen it, so it still gets a row — dropping it silently would leave it bound to a
            // DELIVERED unit and read as feedback the developer ignored.
            boolean overCap = count >= TOP_N_PER_RECIPIENT;
            if (!overCap) {
                perRecipientCount.put(recipient, count + 1);
            }
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
                    .deliveryState(overCap ? FeedbackDeliveryState.SUPPRESSED : FeedbackDeliveryState.PREPARED)
                    .suppressionReason(overCap ? FeedbackSuppressionReason.VOLUME_CAPPED : null)
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
            if (!overCap) {
                newlyPreparedByRecipient.merge(recipient, 1, Integer::sum);
                prepared++;
            }
        }
        if (prepared > 0) {
            log.info("Conversational feedback prepared: jobId={}, units={}", agentJobId, prepared);
        }
        // Published inside this REQUIRES_NEW transaction so AFTER_COMMIT listeners (the Slack nudge) fire
        // exactly when the units are durably visible — and not at all on a pure re-run.
        newlyPreparedByRecipient.forEach((recipient, count) ->
            eventPublisher.publishEvent(new ConversationFeedbackPreparedEvent(workspaceId, recipient, count))
        );
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

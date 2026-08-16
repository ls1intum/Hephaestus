package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.ReflectionFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the REFLECTION feedback units for one cycle's admitted messages.
 *
 * <p>{@code PREPARED} on write and {@code DELIVERED} on the recipient's first read (the compare-and-set
 * in {@code FeedbackRepository#markReflectionDelivered}). We own this surface, so "delivered" can be an
 * observation instead of an assumption.
 *
 * <p>No {@code OutboundEgressGuard} check, deliberately, and unlike every other preparer here: that
 * guard is the last gate before an <em>external</em> write, and Silent Mode means Hephaestus stops
 * talking to third parties. The reflection surface is our own surface and the recipient is the subject, so
 * gating it would turn a "do not post anywhere" switch into "stop recording what we found", which is a
 * different and much larger promise.
 */
@Component
public class ReflectionFeedbackPreparer {

    private static final Logger log = LoggerFactory.getLogger(ReflectionFeedbackPreparer.class);

    /**
     * Cap on reflection units per recipient per cycle. Two, not the conversation lane's three: a
     * process-level message asks the developer to change a habit, and being handed three habits at once
     * is how none of them get changed.
     *
     * <p>The composer is told the same number ({@link ReflectionCompositionInputs}) and its tool refuses
     * a call past it, so in a normal run nothing arrives here to cap. The bound is kept as the last one
     * standing: a runaway turn must not be able to fill a recipient's page.
     */
    public static final int TOP_N_PER_RECIPIENT = 2;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;

    public ReflectionFeedbackPreparer(
        FeedbackRepository feedbackRepository,
        FeedbackObservationRepository feedbackObservationRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackObservationRepository = feedbackObservationRepository;
    }

    /**
     * One routed message and the evidence the router weighed, ready to be written.
     *
     * @param decision {@link ReflectionRoutingDecision#ADMIT} for a message the recipient will see; any
     *     other value is skipped without a row — a refusal that is a property of the evidence is not a
     *     withholding to explain, it is a message that was never owed.
     */
    public record RoutedMessage(
        ComposedReflectionMessage message,
        ReflectionRoutingDecision decision,
        List<Observation> evidence
    ) {}

    /**
     * Prepare REFLECTION units for one recipient in one cycle. Runs REQUIRES_NEW so a preparation failure is
     * isolated from the delivery that already happened; idempotent on a re-run through the
     * {@code (agent_job_id, position)} guard.
     *
     * @param agentJobId  the review job whose run composed these messages; the units share its id, which
     *     is why they take a band of their own rather than starting at position 0
     * @param routed      the cycle's messages for this recipient, in the order they should be offered
     * @param positionBase the first ordinal this recipient's slice of the band may use. Passed in rather
     *     than derived here because {@code (agent_job_id, position)} is unique and one job can file
     *     observations against several people: a base fixed at {@link FeedbackLedgerRecorder#REFLECTION_UNIT_ORDINAL_BASE}
     *     would make the second recipient's first unit collide with the first recipient's, and the
     *     idempotency guard would read that collision as "already written" and drop the row silently.
     * @return the number of units newly prepared this call (0 on a pure re-run)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int prepare(
        UUID agentJobId,
        Long workspaceId,
        Long recipientUserId,
        List<RoutedMessage> routed,
        int positionBase
    ) {
        if (routed.isEmpty()) {
            return 0;
        }
        int lastOrdinal = positionBase + routed.size() - 1;
        if (
            lastOrdinal >=
            FeedbackLedgerRecorder.REFLECTION_UNIT_ORDINAL_BASE + FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH
        ) {
            // Overflowing the band would address the next band's rows and silently drop these writes.
            // A cycle with this many composed messages, or this many recipients, is pathological — fail
            // loudly rather than write into somebody else's ordinals.
            throw new IllegalStateException(
                "Reflection units exceed the ordinal band: jobId=" +
                    agentJobId +
                    ", lastOrdinal=" +
                    lastOrdinal +
                    ", band=" +
                    FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH
            );
        }
        Instant now = Instant.now();
        int position = positionBase;
        int admitted = 0;
        int prepared = 0;
        for (RoutedMessage routedMessage : routed) {
            int unitPosition = position++;
            if (routedMessage.decision() != ReflectionRoutingDecision.ADMIT || admitted >= TOP_N_PER_RECIPIENT) {
                continue;
            }
            admitted++;
            if (feedbackRepository.existsByAgentJobIdAndPosition(agentJobId, unitPosition)) {
                continue;
            }
            Feedback unit = feedbackRepository.save(
                Feedback.builder()
                    .agentJobId(agentJobId)
                    .workspaceId(workspaceId)
                    // Unanchored on purpose: the message is about a habit across several pieces of work,
                    // so naming one of them as "the" artifact would misdescribe what it is evidenced by.
                    // The bound observations carry the artifacts, which is where the evidence belongs.
                    .recipientUserId(recipientUserId)
                    .aboutUserId(recipientUserId)
                    .channel(FeedbackChannel.REFLECTION)
                    .position(unitPosition)
                    .deliveryState(FeedbackDeliveryState.PREPARED)
                    .source(FeedbackSource.AGENT)
                    .body(body(routedMessage.message()))
                    // Cross-run identity for this habit, so a later message about the same practice can
                    // supersede this one rather than stack beside it once supersession is built.
                    .threadKey(threadKey(recipientUserId, routedMessage.message().practiceSlug()))
                    .createdAt(now)
                    .build()
            );
            int ordinal = 0;
            for (Observation observation : routedMessage.evidence()) {
                if (observation.getId() == null) {
                    continue;
                }
                feedbackObservationRepository.insertIfAbsent(
                    unit.getId(),
                    observation.getId(),
                    EvidenceRole.PRIMARY.name(),
                    ordinal++
                );
            }
            prepared++;
        }
        if (prepared > 0) {
            log.info(
                "Reflection feedback prepared: jobId={}, recipientUserId={}, units={}",
                agentJobId,
                recipientUserId,
                prepared
            );
        }
        return prepared;
    }

    /** The stored body — layout owned by {@link ReflectionFeedbackBody}, which is also what reads it back. */
    static String body(ComposedReflectionMessage message) {
        return ReflectionFeedbackBody.render(message.title(), message.body(), message.nextStep());
    }

    /** Stable across runs so successive messages about one person's one habit are one thread. */
    static String threadKey(Long recipientUserId, String practiceSlug) {
        String key = "reflection:" + recipientUserId + ":" + practiceSlug;
        return key.length() <= 64 ? key : key.substring(0, 64);
    }
}

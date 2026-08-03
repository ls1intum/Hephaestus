package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Closes the conversational delivery loop when a mentor turn ends. The mentor surfaces a practice locus by
 * calling the {@code link_finding} custom tool with an observation id; those ids arrive here as
 * {@code linkedFindingIds}. For each, this maps the observation back to its PREPARED CONVERSATION feedback unit,
 * flips it to DELIVERED via a guarded compare-and-set, and - on a winning flip - writes exactly one
 * {@code CONVERSATION_TURN} placement bound to the assistant {@code chat_message} that delivered it.
 *
 * <p>Invoked inside the assistant finalisation transaction after the message is flushed, once the transport outcome
 * is known. The message state and feedback transition therefore commit or roll back together.
 *
 * <p><b>One flip per turn.</b> A single turn may emit several {@code link_finding} events; at most ONE PREPARED unit
 * is delivered per turn - the method returns after the first winning CAS. A rowcount of 0 (a concurrent turn already
 * delivered it, the unit aged out to {@code CONVERSATION_EXPIRED}, or the id was a display-only citation with no
 * PREPARED unit) is a no-op and the loop continues to the next linked id.
 */
@Component
public class ConversationalDeliveryReconciler {

    private static final Logger log = LoggerFactory.getLogger(ConversationalDeliveryReconciler.class);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final ObservationRepository observationRepository;

    public ConversationalDeliveryReconciler(
        FeedbackRepository feedbackRepository,
        FeedbackObservationRepository feedbackObservationRepository,
        FeedbackPlacementRepository feedbackPlacementRepository,
        ObservationRepository observationRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.feedbackPlacementRepository = feedbackPlacementRepository;
        this.observationRepository = observationRepository;
    }

    /**
     * Reconcile the mentor's linked findings for one turn against the PREPARED conversational queue. The caller
     * owns the transaction and has already flushed the assistant message referenced by the placement.
     *
     * @param workspaceId   the chat thread's workspace
     * @param recipientUserId the developer the mentor is talking to (the feedback recipient)
     * @param chatMessageId the assistant {@code chat_message} id this turn produced (the placement's binding)
     * @param linkedFindingIds observation ids the mentor linked this turn, in emission order (duplicates tolerated)
     * @return {@code 1} if exactly one PREPARED unit was flipped + placed this turn, {@code 0} if none matched
     */
    public int reconcile(long workspaceId, long recipientUserId, UUID chatMessageId, List<UUID> linkedFindingIds) {
        if (linkedFindingIds == null || linkedFindingIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        for (UUID observationId : new LinkedHashSet<>(linkedFindingIds)) {
            List<UUID> feedbackIds = feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(
                workspaceId,
                recipientUserId,
                observationId
            );
            if (feedbackIds.isEmpty()) {
                continue;
            }
            // Re-check FeedbackChannelRouter's admission dedup at flip time: if a later re-review has since
            // delivered this locus in-context, skip the flip (let the stale unit age out) so it is not raised twice.
            Observation observation = observationRepository.findById(observationId).orElse(null);
            if (
                observation != null &&
                observation.getRecurrenceKey() != null &&
                feedbackRepository.existsDeliveredInContextForRecurrenceKey(
                    workspaceId,
                    recipientUserId,
                    observation.getRecurrenceKey()
                )
            ) {
                continue;
            }
            UUID feedbackId = feedbackIds.get(0);
            int flipped = feedbackRepository.markConversationDelivered(feedbackId, now);
            if (flipped == 1) {
                Feedback unit = feedbackRepository.getReferenceById(feedbackId);
                feedbackPlacementRepository.save(
                    FeedbackPlacement.builder()
                        .feedback(unit)
                        .placementType(PlacementType.CONVERSATION_TURN)
                        .chatMessageId(chatMessageId)
                        .createdAt(now)
                        .build()
                );
                log.info(
                    "Conversational feedback delivered: feedbackId={}, chatMessageId={}, recipient={}",
                    feedbackId,
                    chatMessageId,
                    recipientUserId
                );
                return 1;
            }
        }
        return 0;
    }

    /**
     * Suppress the single conversational unit this turn would have delivered when the transport was blocked by
     * instance Silent Mode. Unlike a generic transport failure, this is prospective-only: the unit must not remain
     * queued for a later turn after the brake is released.
     */
    public int suppressForSilentMode(long workspaceId, long recipientUserId, List<UUID> linkedFindingIds) {
        if (linkedFindingIds == null || linkedFindingIds.isEmpty()) {
            return 0;
        }
        for (UUID observationId : new LinkedHashSet<>(linkedFindingIds)) {
            List<UUID> feedbackIds = feedbackObservationRepository.findPreparedConversationFeedbackIdsByObservation(
                workspaceId,
                recipientUserId,
                observationId
            );
            if (feedbackIds.isEmpty()) {
                continue;
            }
            UUID feedbackId = feedbackIds.get(0);
            if (feedbackRepository.markConversationSuppressedBySilentMode(feedbackId) == 1) {
                log.info(
                    "Conversational feedback suppressed by instance Silent Mode: feedbackId={}, recipient={}",
                    feedbackId,
                    recipientUserId
                );
                return 1;
            }
        }
        return 0;
    }
}

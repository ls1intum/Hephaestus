package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ConversationalDeliveryReconciler {

    private static final Logger log = LoggerFactory.getLogger(ConversationalDeliveryReconciler.class);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final ObservationRepository observationRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;

    public ConversationalDeliveryReconciler(
        FeedbackRepository feedbackRepository,
        FeedbackObservationRepository feedbackObservationRepository,
        FeedbackPlacementRepository feedbackPlacementRepository,
        ObservationRepository observationRepository,
        ObservationVisibilityPolicy visibilityPolicy
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.feedbackPlacementRepository = feedbackPlacementRepository;
        this.observationRepository = observationRepository;
        this.visibilityPolicy = visibilityPolicy;
    }

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
            Observation observation = observationRepository
                .findByIdAndWorkspaceId(observationId, workspaceId)
                .orElse(null);
            if (
                observation == null ||
                !visibilityPolicy.permits(workspaceId, observation, SourceUsePurpose.CONVERSATIONAL_MENTORING)
            ) {
                continue;
            }
            if (
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

    /** Silent Mode permanently suppresses the unit instead of postponing it. */
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

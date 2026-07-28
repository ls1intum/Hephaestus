package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Practices-internal implementation of {@link ConversationFeedbackErasure}. Runs two scoped bulk deletes —
 * feedback first (DB {@code ON DELETE CASCADE} clears its join/child tables), then the observations. Both
 * statements pin {@code artifact_type = CONVERSATION_THREAD} + workspace (plus the per-method scope), so
 * PR/ISSUE rows and other tenants' rows are never touched — the no-regression contract of the port.
 */
@Component
public class ConversationFeedbackErasureAdapter implements ConversationFeedbackErasure {

    private static final Logger log = LoggerFactory.getLogger(ConversationFeedbackErasureAdapter.class);

    private final FeedbackRepository feedbackRepository;
    private final ObservationRepository observationRepository;

    public ConversationFeedbackErasureAdapter(
        FeedbackRepository feedbackRepository,
        ObservationRepository observationRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.observationRepository = observationRepository;
    }

    @Override
    @Transactional
    public int eraseForThreads(long workspaceId, Collection<Long> slackThreadIds) {
        if (slackThreadIds.isEmpty()) {
            return 0;
        }
        int feedbackDeleted = feedbackRepository.deleteConversationThreadFeedback(workspaceId, slackThreadIds);
        int observationsDeleted = observationRepository.deleteConversationThreadObservations(
            workspaceId,
            slackThreadIds
        );
        if (feedbackDeleted > 0 || observationsDeleted > 0) {
            log.info(
                "Erased conversation-derived practice rows: workspaceId={}, threads={}, feedback={}, observations={}",
                workspaceId,
                slackThreadIds.size(),
                feedbackDeleted,
                observationsDeleted
            );
        }
        return feedbackDeleted + observationsDeleted;
    }

    @Override
    @Transactional
    public int eraseAllConversationForWorkspace(long workspaceId) {
        int feedbackDeleted = feedbackRepository.deleteAllConversationThreadFeedback(workspaceId);
        int observationsDeleted = observationRepository.deleteAllConversationThreadObservations(workspaceId);
        if (feedbackDeleted > 0 || observationsDeleted > 0) {
            log.info(
                "Erased all conversation-derived practice rows for workspace: workspaceId={}, feedback={}, observations={}",
                workspaceId,
                feedbackDeleted,
                observationsDeleted
            );
        }
        return feedbackDeleted + observationsDeleted;
    }

    @Override
    @Transactional
    public int eraseConversationFeedbackAboutUser(long workspaceId, long aboutUserId) {
        int feedbackDeleted = feedbackRepository.deleteConversationThreadFeedbackAboutUser(workspaceId, aboutUserId);
        int observationsDeleted = observationRepository.deleteConversationThreadObservationsAboutUser(
            workspaceId,
            aboutUserId
        );
        if (feedbackDeleted > 0 || observationsDeleted > 0) {
            log.info(
                "Erased conversation-derived practice rows about user: workspaceId={}, aboutUserId={}, feedback={}, observations={}",
                workspaceId,
                aboutUserId,
                feedbackDeleted,
                observationsDeleted
            );
        }
        return feedbackDeleted + observationsDeleted;
    }
}

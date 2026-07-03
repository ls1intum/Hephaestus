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
 * feedback first (its DB {@code ON DELETE CASCADE} clears {@code feedback_observation} / {@code feedback_placement}
 * / {@code feedback_reaction}), then the observations (clearing any remaining {@code feedback_observation} /
 * {@code reaction} children). Both statements pin {@code artifact_type = CONVERSATION_THREAD} +
 * {@code artifact_id ∈ threads} + workspace, so PR/ISSUE rows and other tenants' rows are never touched (the
 * no-regression contract of the port).
 *
 * <p>Module-private (not a {@code NamedInterface}); it is discovered by the {@code practices} container and
 * consumed cross-module only through the {@link ConversationFeedbackErasure} port, exactly like
 * {@link PracticesWorkspacePurgeAdapter}.
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
        // Feedback first: its ON DELETE CASCADE cleans feedback_observation / placement / reaction. Then the
        // observations, clearing any remaining join/reaction children. Both are pinned to CONVERSATION_THREAD +
        // artifact_id ∈ threads + workspace, so no PR/ISSUE or cross-tenant row is in range.
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
}

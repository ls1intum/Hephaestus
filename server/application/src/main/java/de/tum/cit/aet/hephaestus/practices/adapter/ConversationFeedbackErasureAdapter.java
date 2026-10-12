package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Practices-internal implementation of {@link ConversationFeedbackErasure}. Runs two scoped bulk deletes —
 * feedback first (DB {@code ON DELETE CASCADE} clears its join/child tables), then the results. Both
 * statements pin {@code artifact_kind = chat.conversation_thread} + workspace (plus the per-method scope), so
 * PR/ISSUE rows and other tenants' rows are never touched — the no-regression contract of the port.
 */
@Component
public class ConversationFeedbackErasureAdapter implements ConversationFeedbackErasure {

    private static final Logger log = LoggerFactory.getLogger(ConversationFeedbackErasureAdapter.class);

    private final DeliveryPolicyEvaluationRepository evaluationRepository;
    private final FeedbackRepository feedbackRepository;
    private final PracticeResultRepository practiceResultRepository;

    public ConversationFeedbackErasureAdapter(
            DeliveryPolicyEvaluationRepository evaluationRepository,
            FeedbackRepository feedbackRepository,
            PracticeResultRepository practiceResultRepository) {
        this.evaluationRepository = evaluationRepository;
        this.feedbackRepository = feedbackRepository;
        this.practiceResultRepository = practiceResultRepository;
    }

    @Override
    @Transactional
    public int eraseForThreads(long workspaceId, Collection<Long> slackThreadIds) {
        if (slackThreadIds.isEmpty()) {
            return 0;
        }
        evaluationRepository.deleteConversationEvaluationsForThreads(workspaceId, slackThreadIds);
        int feedbackDeleted = feedbackRepository.deleteConversationThreadFeedback(workspaceId, slackThreadIds);
        int practiceResultsDeleted =
                practiceResultRepository.deleteConversationThreadResults(workspaceId, slackThreadIds);
        if (feedbackDeleted > 0 || practiceResultsDeleted > 0) {
            log.info(
                    "Erased conversation-derived practice rows: workspaceId={}, threads={}, feedback={}, results={}",
                    workspaceId,
                    slackThreadIds.size(),
                    feedbackDeleted,
                    practiceResultsDeleted);
        }
        return feedbackDeleted + practiceResultsDeleted;
    }

    @Override
    @Transactional
    public int eraseAllConversationForWorkspace(long workspaceId) {
        evaluationRepository.deleteAllConversationEvaluations(workspaceId);
        int feedbackDeleted = feedbackRepository.deleteAllConversationThreadFeedback(workspaceId);
        int practiceResultsDeleted = practiceResultRepository.deleteAllConversationThreadResults(workspaceId);
        if (feedbackDeleted > 0 || practiceResultsDeleted > 0) {
            log.info(
                    "Erased all conversation-derived practice rows for workspace: workspaceId={}, feedback={}, results={}",
                    workspaceId,
                    feedbackDeleted,
                    practiceResultsDeleted);
        }
        return feedbackDeleted + practiceResultsDeleted;
    }

    @Override
    @Transactional
    public int eraseConversationFeedbackAboutUser(long workspaceId, long aboutUserId) {
        evaluationRepository.deleteConversationEvaluationsAboutUser(workspaceId, aboutUserId);
        int feedbackDeleted = feedbackRepository.deleteConversationThreadFeedbackAboutUser(workspaceId, aboutUserId);
        int practiceResultsDeleted =
                practiceResultRepository.deleteConversationThreadResultsAboutUser(workspaceId, aboutUserId);
        if (feedbackDeleted > 0 || practiceResultsDeleted > 0) {
            log.info(
                    "Erased conversation-derived practice rows about user: workspaceId={}, aboutUserId={}, feedback={}, results={}",
                    workspaceId,
                    aboutUserId,
                    feedbackDeleted,
                    practiceResultsDeleted);
        }
        return feedbackDeleted + practiceResultsDeleted;
    }
}

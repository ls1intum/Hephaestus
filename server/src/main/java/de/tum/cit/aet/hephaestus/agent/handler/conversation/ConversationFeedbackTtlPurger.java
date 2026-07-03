package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-workspace transactional boundary for the conversational-feedback TTL sweep. Separate bean (not a self-invoked
 * method on {@link ConversationFeedbackTtlSweeper}) so the {@code REQUIRES_NEW} boundary actually takes effect across
 * a proxy hop - one poisoned workspace's rollback then cannot unwind the whole sweep. The update carries the
 * {@code workspace_id} predicate the tenancy inspector requires (the native expiry query bypasses the inspector).
 */
@Component
public class ConversationFeedbackTtlPurger {

    private final FeedbackRepository feedbackRepository;

    public ConversationFeedbackTtlPurger(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Age out every PREPARED CONVERSATION unit for {@code workspaceId} created strictly before {@code cutoff} to
     * SUPPRESSED / CONVERSATION_EXPIRED, in a fresh transaction isolated from the other workspaces in the sweep.
     *
     * @return the number of units expired
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireWorkspaceBefore(long workspaceId, Instant cutoff) {
        return feedbackRepository.expirePreparedConversationBefore(workspaceId, cutoff);
    }
}

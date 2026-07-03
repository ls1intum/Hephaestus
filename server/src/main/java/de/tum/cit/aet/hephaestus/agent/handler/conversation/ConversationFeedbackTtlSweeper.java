package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ages out PREPARED conversational feedback that was never raised in a mentor turn within the TTL window. After
 * {@value #TTL_DAYS} days a still-PREPARED unit is flipped to SUPPRESSED / {@code CONVERSATION_EXPIRED}.
 *
 * <p>This bean owns only scheduling, cross-pod locking, and window arithmetic; the per-workspace delete runs in
 * {@link ConversationFeedbackTtlPurger}'s own {@code REQUIRES_NEW} transaction so a failure is isolated. It is
 * {@link WorkspaceAgnostic} because the enumeration query is unscoped and the sweep is inherently cross-workspace,
 * and gated to the server role with {@link SchedulerLock} - the same pattern as {@code SlackRetentionSweeper}.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Expiring stale PREPARED conversational feedback across all workspaces on a bounded-TTL schedule")
public class ConversationFeedbackTtlSweeper {

    private static final Logger log = LoggerFactory.getLogger(ConversationFeedbackTtlSweeper.class);

    /** How long a PREPARED conversational unit may wait to be raised before it ages out. */
    static final int TTL_DAYS = 14;

    private final FeedbackRepository feedbackRepository;
    private final ConversationFeedbackTtlPurger purger;

    public ConversationFeedbackTtlSweeper(FeedbackRepository feedbackRepository, ConversationFeedbackTtlPurger purger) {
        this.feedbackRepository = feedbackRepository;
        this.purger = purger;
    }

    @Scheduled(cron = "0 45 3 * * *")
    @SchedulerLock(name = "conversation-feedback-ttl-sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void sweep() {
        sweepNow(Instant.now());
    }

    /**
     * Run the sweep immediately across every workspace holding PREPARED conversational units, expiring those older
     * than the TTL relative to {@code now}. Exposed so tests can advance the clock deterministically.
     *
     * @param now the reference instant (the cutoff is {@code now - TTL_DAYS})
     * @return the total number of units expired across all workspaces
     */
    public long sweepNow(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(TTL_DAYS));
        List<Long> workspaceIds = feedbackRepository.findWorkspaceIdsWithPreparedConversation();
        long totalExpired = 0;
        for (Long workspaceId : workspaceIds) {
            try {
                totalExpired += purger.expireWorkspaceBefore(workspaceId, cutoff);
            } catch (RuntimeException e) {
                log.warn("conversation.ttl: sweep failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        }
        if (totalExpired > 0) {
            log.info(
                "conversation.ttl: expired {} prepared unit(s) across {} workspace(s)",
                totalExpired,
                workspaceIds.size()
            );
        }
        return totalExpired;
    }
}

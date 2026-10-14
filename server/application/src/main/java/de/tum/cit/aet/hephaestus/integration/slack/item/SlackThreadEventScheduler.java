package de.tum.cit.aet.hephaestus.integration.slack.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadEvents;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationCandidateSource;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequested;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackThreadEventScheduler {
    private static final int MIN_TURNS = 4;
    private static final int MIN_GROWTH = 2;
    private static final int QUIESCENCE_MINUTES = 10;
    private final SlackConversationCandidateSource candidates;
    private final ThreadItemSource source;
    private final ItemWriter items;
    private final SlackThreadRepository threads;
    private final ReviewService reviews;
    private final TransactionTemplate transactions;
    private final boolean enabled;

    public SlackThreadEventScheduler(
            SlackConversationCandidateSource candidates,
            ThreadItemSource source,
            ItemWriter items,
            SlackThreadRepository threads,
            ReviewService reviews,
            TransactionTemplate transactions,
            @Value("${hephaestus.integration.slack.conversation-ingest.enabled:true}") boolean enabled) {
        this.candidates = candidates;
        this.source = source;
        this.items = items;
        this.threads = threads;
        this.reviews = reviews;
        this.transactions = transactions;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "conversation-thread-review", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void sweep() {
        sweepNow();
    }

    @WorkspaceAgnostic("Cross-workspace Slack source discovery writes an explicit workspace on every item and event")
    public long sweepNow() {
        if (!enabled) return 0;
        Instant now = Instant.now();
        long offered = 0;
        for (var candidate : candidates.settledCandidates(MIN_TURNS)) {
            Long last = SlackTs.toEpochMicros(candidate.lastTs());
            if (last == null
                    || Instant.ofEpochSecond(last / 1_000_000L, last % 1_000_000L * 1_000L)
                            .isAfter(now.minusSeconds(QUIESCENCE_MINUTES * 60L))) continue;
            if (candidates.liveTurnCount(candidate.workspaceId(), candidate.channelId(), candidate.threadTs())
                            < MIN_TURNS
                    || candidates.liveTurnCountSince(
                                    candidate.workspaceId(),
                                    candidate.channelId(),
                                    candidate.threadTs(),
                                    candidate.lastReviewedTs())
                            < MIN_GROWTH) continue;
            Boolean recorded = transactions.execute(status -> {
                var item = items.upsert(new ItemRegistration(
                        candidate.workspaceId(), ThreadEvents.THREAD, candidate.threadId(), null, null));
                var pinned = source.current(item);
                if (pinned.isEmpty()) return false;
                items.record(new ItemEventInput(
                        candidate.workspaceId(),
                        item.itemId(),
                        ThreadEvents.SETTLED,
                        pinned.get().version().value(),
                        now,
                        FoundBy.SYNC,
                        null,
                        null));
                return true;
            });
            if (Boolean.TRUE.equals(recorded)) offered++;
        }
        return offered;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewRequested(ReviewRequested event) {
        transactions.executeWithoutResult(status -> reviews.find(event.workspaceId(), event.reviewId())
                .ifPresent(review -> {
                    if (!ThreadEvents.THREAD.equals(review.item().kind()) || review.version() == null) return;
                    Long sourceId = review.item().sourceId();
                    if (sourceId == null) return;
                    source.current(review.item())
                            .filter(current -> current.version().equals(review.version()))
                            .ifPresent(current -> threads.findByWorkspaceIdAndId(event.workspaceId(), sourceId)
                                    .ifPresent(thread -> {
                                        String lastTs = thread.getLastTs();
                                        if (lastTs != null)
                                            candidates.markReviewed(event.workspaceId(), sourceId, lastTs);
                                    }));
                }));
    }
}

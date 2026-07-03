package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackEventDedupRepository;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, multi-replica event dedup for the Slack Events API. Replaces the in-memory {@code Set} that only
 * suppressed duplicates seen by one controller instance: Slack redelivers un-acked events, and two pods can each
 * receive the same delivery, so the suppression must live in the shared DB.
 *
 * <p>{@link #claim(String)} is first-writer-wins: the underlying {@code INSERT … ON CONFLICT DO NOTHING} commits a
 * marker row for {@code eventId} and reports whether <em>this</em> replica won the race (and should therefore
 * process the event). Because the insert commits when this method returns, a second replica's insert either blocks
 * on the unique index until this transaction commits and then sees the conflict, or arrives after commit and sees
 * it directly — either way exactly one caller gets {@code true}.
 *
 * <p>The table is workspace-independent (the controller dedups before it knows the workspace), so this service is
 * {@link WorkspaceAgnostic}. A daily sweep prunes markers past their TTL — the TTL is generous relative to Slack's
 * retry horizon, so a pruned row can never resurrect a still-retrying duplicate.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Event dedup keys on the raw Slack event_id, which is workspace-independent")
public class SlackEventDedupService {

    private static final Logger log = LoggerFactory.getLogger(SlackEventDedupService.class);

    private final SlackEventDedupRepository repository;
    private final Duration ttl;

    public SlackEventDedupService(
        SlackEventDedupRepository repository,
        @Value("${hephaestus.integration.slack.dedup.ttl:PT48H}") Duration ttl
    ) {
        this.repository = repository;
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofHours(48) : ttl;
    }

    /**
     * @return {@code true} iff this replica just claimed {@code eventId} (process the event); {@code false} if it
     *     was already claimed (drop the duplicate).
     */
    @Transactional
    public boolean claim(String eventId) {
        Instant now = Instant.now();
        return repository.claim(eventId, now, now.plus(ttl)) > 0;
    }

    /** Daily retention sweep for expired dedup markers. Only fires on the scheduling-enabled (server) role. */
    @Scheduled(cron = "0 45 3 * * *")
    @SchedulerLock(name = "slack-event-dedup-prune", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    @Transactional
    public void pruneExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.debug("slack.dedup: pruned {} expired event marker(s)", deleted);
        }
    }
}

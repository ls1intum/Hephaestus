package de.tum.in.www1.hephaestus.agent.mentor.chat;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically marks assistant rows whose {@code status='in_flight'} predates the configured
 * window as {@code interrupted}. The unique partial index on {@code chat_message(thread_id)
 * WHERE status='in_flight'} would otherwise block every future turn on that thread after a
 * JVM crash.
 */
@Component
@WorkspaceAgnostic("Sweeps stuck rows by created_at; not a tenant data accessor")
public class MentorInFlightReaper {

    private static final Logger log = LoggerFactory.getLogger(MentorInFlightReaper.class);

    private final ChatMessageRepository chatMessageRepository;
    private final Duration window;

    public MentorInFlightReaper(
        ChatMessageRepository chatMessageRepository,
        @Value("${hephaestus.mentor.in-flight-reaper.window:PT10M}") Duration window
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.window = window;
    }

    /** Fires on the cron defined in {@code hephaestus.mentor.in-flight-reaper.cron} (default every 2 minutes). */
    @Scheduled(cron = "${hephaestus.mentor.in-flight-reaper.cron:0 */2 * * * *}")
    public void reap() {
        int updated = chatMessageRepository.reapStaleInFlight(Instant.now().minus(window));
        if (updated > 0) {
            log.info("Mentor in-flight reaper marked {} stuck row(s) as interrupted (window={})", updated, window);
        }
    }

    Duration window() {
        return window;
    }
}

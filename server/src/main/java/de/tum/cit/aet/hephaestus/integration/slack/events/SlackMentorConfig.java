package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded worker pool the {@link SlackEventDispatcher} hands post-ACK mentor DM work to. A DM drives a remote
 * Slack {@code assistant.threads.setStatus} call (and possibly a canned duty-of-care / not-linked / over-cap
 * {@code chat.postMessage}) before the mentor turn is dispatched, so running it on the request thread would risk
 * breaching Slack's 3&nbsp;s Events-API ACK window (→ Slack marks the delivery failed and retries, a retry storm).
 *
 * <p>The events controller keeps the fast, durable pre-ACK work synchronous (signature verify, dedup claim, and
 * durable channel-message ingest); only the slow/remote DM mentor path is offloaded here — mirroring how
 * {@code SlackInteractivityController} offloads its post-ACK work. The mentor turn itself
 * ({@code MentorTurnRunner#run}) is already non-blocking, so a task here finishes quickly (just the one or two
 * Slack round-trips), and a small pool suffices.
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorConfig {

    @Bean(name = "slackMentorDmExecutor")
    Executor slackMentorDmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("slack-mentor-dm-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}

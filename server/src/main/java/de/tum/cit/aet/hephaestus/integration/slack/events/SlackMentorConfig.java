package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded worker pool the {@link SlackEventDispatcher} hands its post-ACK Slack work to. A DM, for example,
 * drives a remote Slack {@code assistant.threads.setStatus} call (and possibly a canned duty-of-care / not-linked / over-cap
 * {@code chat.postMessage}) before the mentor turn is dispatched, so running it on the request thread would risk
 * breaching Slack's 3&nbsp;s Events-API ACK window (→ Slack marks the delivery failed and retries, a retry storm).
 *
 * <p>The events controller keeps the fast pre-ACK work synchronous (signature verify, dedup claim, and the
 * best-effort channel-message ingest); the slow/remote Slack paths are offloaded here, mirroring how
 * {@code SlackInteractivityController} offloads its post-ACK work. The mentor turn itself
 * ({@code MentorTurnRunner#run}) is already non-blocking, so a task here finishes quickly (just the one or two
 * Slack round-trips), and a small pool suffices.
 *
 * <p><strong>Two pools, isolated by purpose.</strong> {@code slackMentorDmExecutor} carries DM mentor turns (the
 * hot path, which can burst when many members chat at once). {@code slackHomeExecutor} carries the privacy/consent
 * surfaces — the App Home re-render + onboarding CTA and the {@code assistant_thread_started} suggested-prompt
 * seed. Keeping them on separate pools means a DM burst cannot starve or delay the App Home render that shows the
 * privacy disclosure and research-consent toggle. The Home surfaces are low-frequency and best-effort (a dropped
 * render re-fires on the next open), so a tiny bounded pool is enough.
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

    /**
     * Dedicated small pool for the App Home render + onboarding CTA and the assistant-thread suggested-prompt seed.
     * Isolated from {@code slackMentorDmExecutor} so a DM burst can never delay the privacy-disclosure/consent Home
     * render. Low-frequency and best-effort, so a core of 1 / max of 2 with a small bounded queue suffices; a
     * saturated pool simply drops the task (the surface re-renders on the next open).
     */
    @Bean(name = "slackHomeExecutor")
    Executor slackHomeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("slack-home-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}

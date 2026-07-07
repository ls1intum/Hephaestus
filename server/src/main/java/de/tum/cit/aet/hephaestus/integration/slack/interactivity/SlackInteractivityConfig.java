package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded worker pool the {@link SlackInteractivityController} hands post-ACK interactivity work to (rating
 * writes, App Home consent toggles). Kept small — Slack interactivity is bursty but low-volume, and the ACK has
 * already been sent, so a brief queue is preferable to holding the request thread.
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackInteractivityConfig {

    @Bean(name = "slackInteractivityExecutor")
    Executor slackInteractivityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("slack-interactivity-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
